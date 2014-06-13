package com.ianhanniballake.contractiontimer.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.database.Cursor;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.CursorAdapter;
import android.support.v7.app.ActionBarActivity;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.android.gms.tagmanager.DataLayer;
import com.ianhanniballake.contractiontimer.BuildConfig;
import com.ianhanniballake.contractiontimer.R;
import com.ianhanniballake.contractiontimer.provider.ContractionContract;
import com.ianhanniballake.contractiontimer.tagmanager.GtmManager;

/**
 * Main Activity for managing contractions
 */
public class MainActivity extends ActionBarActivity implements LoaderManager.LoaderCallbacks<Cursor> {
    /**
     * Intent extra used to signify that this activity was launched from a widget
     */
    public final static String LAUNCHED_FROM_WIDGET_EXTRA = "com.ianhanniballake.contractiontimer.LaunchedFromWidget";
    /**
     * Intent extra used to signify that this activity was launched from the notification
     */
    public final static String LAUNCHED_FROM_NOTIFICATION_EXTRA =
            "com.ianhanniballake.contractiontimer.LaunchedFromNotification";
    /**
     * Intent extra used to signify that this activity was launched from the notification's Add/Edit Note action
     */
    public final static String LAUNCHED_FROM_NOTIFICATION_ACTION_NOTE_EXTRA =
            "com.ianhanniballake.contractiontimer.LaunchedFromNotificationActionNote";
    private final static String TAG = MainActivity.class.getSimpleName();
    /**
     * BroadcastReceiver listening for NOTE_CLOSE_ACTION and RESET_CLOSE_ACTION actions
     */
    private final BroadcastReceiver dialogFragmentClosedBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "DialogFragmentClosedBR Received " + intent.getAction());
            GtmManager.getInstance(MainActivity.this).pushOpenScreen("Main");
        }
    };
    /**
     * Adapter to store and manage the current cursor
     */
    private CursorAdapter adapter;

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = getIntent();
        // If there is no data associated with the Intent, sets the data to the
        // default URI, which accesses all contractions.
        if (intent.getData() == null)
            intent.setData(ContractionContract.Contractions.CONTENT_URI);
        setContentView(R.layout.activity_main);
        if (savedInstanceState == null)
            showFragments();
        adapter = new CursorAdapter(this, null, 0) {
            @Override
            public void bindView(final View view, final Context context, final Cursor cursor) {
                // Nothing to do
            }

            @Override
            public View newView(final Context context, final Cursor cursor, final ViewGroup parent) {
                return null;
            }
        };
        getSupportLoaderManager().initLoader(0, null, this);
    }

    @Override
    public Loader<Cursor> onCreateLoader(final int id, final Bundle args) {
        final String[] projection = {BaseColumns._ID, ContractionContract.Contractions.COLUMN_NAME_START_TIME,
                ContractionContract.Contractions.COLUMN_NAME_END_TIME,
                ContractionContract.Contractions.COLUMN_NAME_NOTE};
        final String selection = ContractionContract.Contractions.COLUMN_NAME_START_TIME + ">?";
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final long averagesTimeFrame = Long.parseLong(preferences.getString(
                Preferences.AVERAGE_TIME_FRAME_PREFERENCE_KEY,
                getString(R.string.pref_settings_average_time_frame_default)));
        final long timeCutoff = System.currentTimeMillis() - averagesTimeFrame;
        final String[] selectionArgs = {Long.toString(timeCutoff)};
        return new CursorLoader(this, getIntent().getData(), projection, selection, selectionArgs, null);
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        getMenuInflater().inflate(R.menu.activity_main_reset, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public void onLoaderReset(final Loader<Cursor> loader) {
        adapter.swapCursor(null);
        supportInvalidateOptionsMenu();
    }

    @Override
    public void onLoadFinished(final Loader<Cursor> loader, final Cursor data) {
        adapter.swapCursor(data);
        supportInvalidateOptionsMenu();
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        GtmManager gtmManager = GtmManager.getInstance(this);
        gtmManager.push(DataLayer.mapOf("menu", "Menu", "count", adapter.getCount()));
        switch (item.getItemId()) {
            case R.id.menu_share:
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Menu selected Share");
                gtmManager.pushEvent("Share");
                shareContractions();
                return true;
            case R.id.menu_add:
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Menu selected Add");
                gtmManager.pushEvent("Add");
                final Intent addIntent = new Intent(Intent.ACTION_INSERT, getIntent().getData())
                        .setPackage(getPackageName());
                startActivity(addIntent);
                return true;
            case R.id.menu_reset:
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Menu selected Reset");
                gtmManager.pushEvent("Reset");
                final ResetDialogFragment resetDialogFragment = new ResetDialogFragment();
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Showing Dialog");
                gtmManager.pushOpenScreen("Reset");
                resetDialogFragment.show(getSupportFragmentManager(), "reset");
                return true;
            case R.id.menu_settings:
                startActivity(new Intent(this, Preferences.class));
                return true;
            case R.id.menu_donate:
                if (BuildConfig.DEBUG)
                    Log.d(TAG, "Menu selected Donate");
                gtmManager.pushEvent("Donate");
                startActivity(new Intent(this, DonateActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final int contractionCount = adapter == null ? 0 : adapter.getCount();
        final boolean hasContractions = contractionCount > 0;
        final MenuItem share = menu.findItem(R.id.menu_share);
        share.setEnabled(hasContractions);
        final MenuItem reset = menu.findItem(R.id.menu_reset);
        reset.setEnabled(hasContractions);
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean isKeepScreenOn = preferences.getBoolean(Preferences.KEEP_SCREEN_ON_PREFERENCE_KEY, getResources()
                .getBoolean(R.bool.pref_settings_keep_screen_on_default));
        if (BuildConfig.DEBUG)
            Log.d(TAG, "Keep Screen On: " + isKeepScreenOn);
        if (isKeepScreenOn)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final boolean isLockPortrait = preferences.getBoolean(Preferences.LOCK_PORTRAIT_PREFERENCE_KEY, getResources()
                .getBoolean(R.bool.pref_settings_lock_portrait_default));
        if (BuildConfig.DEBUG)
            Log.d(TAG, "Lock Portrait: " + isLockPortrait);
        if (isLockPortrait)
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        else
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
        final boolean averageTimeFrameChanged = preferences.getBoolean(
                Preferences.AVERAGE_TIME_FRAME_CHANGED_MAIN_PREFERENCE_KEY, false);
        if (averageTimeFrameChanged) {
            final Editor editor = preferences.edit();
            editor.remove(Preferences.AVERAGE_TIME_FRAME_CHANGED_MAIN_PREFERENCE_KEY);
            editor.commit();
            getSupportLoaderManager().restartLoader(0, null, this);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        GtmManager gtmManager = GtmManager.getInstance(this);
        gtmManager.pushOpenScreen("Main");
        Intent intent = getIntent();
        if (intent.hasExtra(MainActivity.LAUNCHED_FROM_WIDGET_EXTRA)) {
            final String widgetIdentifier = intent.getStringExtra(MainActivity.LAUNCHED_FROM_WIDGET_EXTRA);
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Launched from " + widgetIdentifier);
            gtmManager.pushEvent("Launch", DataLayer.mapOf("widget", widgetIdentifier,
                    "type", DataLayer.OBJECT_NOT_PRESENT));
            intent.removeExtra(MainActivity.LAUNCHED_FROM_WIDGET_EXTRA);
        }
        if (intent.hasExtra(MainActivity.LAUNCHED_FROM_NOTIFICATION_EXTRA)) {
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Launched from Notification");
            gtmManager.pushEvent("Launch", DataLayer.mapOf("widget", "Notification",
                    "type", DataLayer.OBJECT_NOT_PRESENT));
            intent.removeExtra(MainActivity.LAUNCHED_FROM_NOTIFICATION_EXTRA);
        }
        if (intent.hasExtra(MainActivity.LAUNCHED_FROM_NOTIFICATION_ACTION_NOTE_EXTRA)) {
            long id = intent.getLongExtra(BaseColumns._ID, -1L);
            String existingNote = intent.getStringExtra(ContractionContract.Contractions.COLUMN_NAME_NOTE);
            String type = TextUtils.isEmpty(existingNote) ? "Add Note" : "Edit Note";
            if (BuildConfig.DEBUG)
                Log.d(TAG, "Launched from Notification " + type + " action");
            gtmManager.push("type", type);
            gtmManager.pushEvent("Launch", DataLayer.mapOf("widget", "NotificationAction"));
            gtmManager.pushEvent("Note", DataLayer.mapOf("menu", "NotificationAction",
                    "position", DataLayer.OBJECT_NOT_PRESENT));
            final NoteDialogFragment noteDialogFragment = new NoteDialogFragment();
            final Bundle args = new Bundle();
            args.putLong(NoteDialogFragment.CONTRACTION_ID_ARGUMENT, id);
            args.putString(NoteDialogFragment.EXISTING_NOTE_ARGUMENT, existingNote);
            noteDialogFragment.setArguments(args);
            gtmManager.pushOpenScreen(TextUtils.isEmpty(existingNote) ? "NoteAdd" : "NoteEdit");
            noteDialogFragment.show(getSupportFragmentManager(), "note");
            intent.removeExtra(MainActivity.LAUNCHED_FROM_NOTIFICATION_ACTION_NOTE_EXTRA);
        }
        final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        final IntentFilter dialogCloseFilter = new IntentFilter();
        dialogCloseFilter.addAction(NoteDialogFragment.NOTE_CLOSE_ACTION);
        dialogCloseFilter.addAction(ResetDialogFragment.RESET_CLOSE_ACTION);
        localBroadcastManager.registerReceiver(dialogFragmentClosedBroadcastReceiver, dialogCloseFilter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        final LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(this);
        localBroadcastManager.unregisterReceiver(dialogFragmentClosedBroadcastReceiver);
    }

    /**
     * Builds the averages data to share and opens the Intent chooser
     */
    private void shareContractions() {
        final Cursor data = adapter.getCursor();
        if (data.getCount() == 0)
            return;
        final TextView averageDurationView = (TextView) findViewById(R.id.average_duration);
        final TextView averageFrequencyView = (TextView) findViewById(R.id.average_frequency);
        data.moveToLast();
        final int startTimeColumnIndex = data.getColumnIndex(ContractionContract.Contractions.COLUMN_NAME_START_TIME);
        final long lastStartTime = data.getLong(startTimeColumnIndex);
        final int count = adapter.getCount();
        final CharSequence relativeTimeSpan = DateUtils.getRelativeTimeSpanString(lastStartTime,
                System.currentTimeMillis(), 0);
        final String formattedData = getResources().getQuantityString(
                R.plurals.share_average,
                count,
                relativeTimeSpan,
                count,
                averageDurationView.getText(),
                averageFrequencyView.getText());
        ShareCompat.IntentBuilder.from(this).setSubject(getString(R.string.share_subject))
                .setType("text/plain").setText(formattedData).setChooserTitle(R.string.share_pick_application)
                .startChooser();
    }

    /**
     * Creates and shows the fragments for the MainActivity
     */
    private void showFragments() {
        final ContractionControlsFragment controlsFragment = new ContractionControlsFragment();
        ContractionListFragment listFragment;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
            listFragment = new ContractionListFragmentV11();
        else
            listFragment = new ContractionListFragmentBase();
        final ContractionAverageFragment averageFragment = new ContractionAverageFragment();
        // Execute a transaction, replacing any existing fragment
        // with this one inside the frame.
        final FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.replace(R.id.controls, controlsFragment);
        ft.replace(R.id.list, listFragment);
        ft.replace(R.id.averages, averageFragment);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        ft.commit();
    }
}