package com.ianhanniballake.contractiontimer.notification;

import android.app.IntentService;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.preference.PreferenceManager;
import android.preview.support.v4.app.NotificationManagerCompat;
import android.provider.BaseColumns;
import android.support.v4.app.NotificationCompat;
import android.text.format.DateUtils;

import com.ianhanniballake.contractiontimer.R;
import com.ianhanniballake.contractiontimer.appwidget.AppWidgetToggleService;
import com.ianhanniballake.contractiontimer.provider.ContractionContract;
import com.ianhanniballake.contractiontimer.ui.Preferences;

/**
 * IntentService which updates the ongoing notification
 */
public class NotificationUpdateService extends IntentService {
    private static final int NOTIFICATION_ID = 0;

    public NotificationUpdateService() {
        super(NotificationUpdateService.class.getSimpleName());
    }

    public static void updateNotification(Context context) {
        context.startService(new Intent(context, NotificationUpdateService.class));
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        final boolean notificationsEnabled = preferences.getBoolean(Preferences.NOTIFICATION_ENABLE_PREFERENCE_KEY,
                getResources().getBoolean(R.bool.pref_notification_enable_default));
        if (!notificationsEnabled) {
            notificationManager.cancel(NOTIFICATION_ID);
            return;
        }
        final String[] projection = {BaseColumns._ID, ContractionContract.Contractions.COLUMN_NAME_START_TIME,
                ContractionContract.Contractions.COLUMN_NAME_END_TIME};
        final String selection = ContractionContract.Contractions.COLUMN_NAME_START_TIME + ">?";
        final long averagesTimeFrame = Long.parseLong(preferences.getString(
                Preferences.AVERAGE_TIME_FRAME_PREFERENCE_KEY,
                getString(R.string.pref_settings_average_time_frame_default)));
        final long timeCutoff = System.currentTimeMillis() - averagesTimeFrame;
        final String[] selectionArgs = {Long.toString(timeCutoff)};
        final Cursor data = getContentResolver().query(ContractionContract.Contractions.CONTENT_URI, projection,
                selection, selectionArgs, null);
        if (data == null || data.getCount() == 0) {
            notificationManager.cancel(NOTIFICATION_ID);
            if (data != null) {
                data.close();
            }
            return;
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setSmallIcon(R.drawable.ic_notification);
        Intent contentIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        contentIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK |
                Intent.FLAG_ACTIVITY_TASK_ON_HOME);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent);
        // Get the average duration and frequency
        double averageDuration = 0;
        double averageFrequency = 0;
        int numDurations = 0;
        int numFrequencies = 0;
        final int startTimeColumnIndex = data
                .getColumnIndex(ContractionContract.Contractions.COLUMN_NAME_START_TIME);
        final int endTimeColumnIndex = data
                .getColumnIndex(ContractionContract.Contractions.COLUMN_NAME_END_TIME);
        while (data.moveToNext()) {
            final long startTime = data.getLong(startTimeColumnIndex);
            if (!data.isNull(endTimeColumnIndex)) {
                final long endTime = data.getLong(endTimeColumnIndex);
                final long curDuration = endTime - startTime;
                averageDuration = (curDuration + numDurations * averageDuration) / (numDurations + 1);
                numDurations++;
            }
            if (data.moveToNext()) {
                final int prevContractionStartTimeColumnIndex = data
                        .getColumnIndex(ContractionContract.Contractions.COLUMN_NAME_START_TIME);
                final long prevContractionStartTime = data.getLong(prevContractionStartTimeColumnIndex);
                final long curFrequency = startTime - prevContractionStartTime;
                averageFrequency = (curFrequency + numFrequencies * averageFrequency) / (numFrequencies + 1);
                numFrequencies++;
            }
        }
        final long averageDurationInSeconds = (long) (averageDuration / 1000);
        String formattedAverageDuration = DateUtils.formatElapsedTime(averageDurationInSeconds);
        final long averageFrequencyInSeconds = (long) (averageFrequency / 1000);
        String formattedAverageFrequency = DateUtils.formatElapsedTime(averageFrequencyInSeconds);
        String contentText = getString(R.string.notification_content_text,
                formattedAverageDuration, formattedAverageFrequency);
        String bigText = getString(R.string.notification_big_text,
                formattedAverageDuration, formattedAverageFrequency);
        builder.setContentText(contentText)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(bigText));
        // Determine whether a contraction is currently ongoing
        final boolean contractionOngoing = data.moveToFirst()
                && data.isNull(data.getColumnIndex(ContractionContract.Contractions.COLUMN_NAME_END_TIME));
        builder.setOngoing(contractionOngoing);
        Intent startStopIntent = new Intent(this, AppWidgetToggleService.class);
        PendingIntent startStopPendingIntent = PendingIntent.getService(this, 0, startStopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        if (contractionOngoing) {
            builder.setContentTitle(getString(R.string.notification_timing));
            builder.addAction(R.drawable.ic_action_stop, getString(R.string.contraction_end),
                    startStopPendingIntent);
        } else {
            builder.setContentTitle(getString(R.string.app_name));
            builder.addAction(R.drawable.ic_action_start, getString(R.string.contraction_start),
                    startStopPendingIntent);
        }
        final long when = contractionOngoing ? data.getLong(startTimeColumnIndex) : data.getLong(endTimeColumnIndex);
        builder.setWhen(when);
        builder.setUsesChronometer(true);
        // Close the cursor
        data.close();
        notificationManager.notify(NOTIFICATION_ID, builder.build());
    }
}
