package is.pedals.backgroundyoutube;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.annotation.Nullable;
import android.util.Log;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.devbrackets.android.exomedia.EMNotification;
import com.devbrackets.android.exomedia.event.EMMediaProgressEvent;
import com.devbrackets.android.exomedia.service.EMPlaylistService;

import java.util.concurrent.TimeUnit;

//copied from exomediademo/service/AudioService.java
public class MediaPlayerService extends EMPlaylistService<MediaItem, PlaylistManager> {

    public static final String PLAY_IN_YOUTUBE = "playinyoutube";
    private static final String TAG = "MediaPlayerService";
    private static final int NOTIFICATION_ID = 1564; //Arbitrary
    private static final int FOREGROUND_REQUEST_CODE = 332; //Arbitrary
    private static final float AUDIO_DUCK_VOLUME = 0.1f;

    private Bitmap largeNotificationImage;

    @Override
    protected void onServiceCreate() {
        super.onServiceCreate();
        //use our customized Notification class
        notificationHelper = new BYtNotification(getApplicationContext());
    }

    @Override
    protected String getAppName() {
        return getResources().getString(R.string.app_name);
    }

    @Override
    protected int getNotificationId() {
        return NOTIFICATION_ID;
    }

    @Override
    protected float getAudioDuckVolume() {
        return AUDIO_DUCK_VOLUME;
    }

    @Override
    protected PlaylistManager getMediaPlaylistManager() {
        return App.getPlaylistManager();
    }

    @Override
    protected PendingIntent getNotificationClickPendingIntent() {
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.setAction(PLAY_IN_YOUTUBE);
        return PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Nullable
    @Override
    protected Bitmap getLargeNotificationImage() {
        return largeNotificationImage;
    }

    @Override
    protected Bitmap getDefaultLargeNotificationImage() {
        return null;
    }

    @Nullable
    @Override
    protected Bitmap getDefaultLargeNotificationSecondaryImage() {
        return null;
    }

    @Override
    protected void updateLargeNotificationImage(int size, MediaItem playlistItem) {
        Glide.with(getApplicationContext())
                .load(playlistItem.getThumbnailUrl())
                .asBitmap()
                .into(new SimpleTarget<Bitmap>(size, size) {
                    @Override
                    public void onResourceReady(Bitmap bitmap, GlideAnimation anim) {
                        largeNotificationImage = bitmap;
                        onLargeNotificationImageUpdated();
                    }
                });
    }

    @Override
    protected int getNotificationIconRes() {
        return R.mipmap.ic_launcher;
    }

    @Override
    protected int getLockScreenIconRes() {
        return R.mipmap.ic_launcher;
    }

    @Override
    //necessary as there is currently a bug in EMPlaylistService
    protected void mediaItemChanged(MediaItem currentItem) {
        currentMediaType = getMediaPlaylistManager().getCurrentItemType();

        //Validates that the currentPlaylistItem is for the currentItem
        if (!getMediaPlaylistManager().isPlayingItem(currentPlaylistItem)) {
            Log.d(TAG, "forcing currentPlaylistItem update");
            currentPlaylistItem = getMediaPlaylistManager().getCurrentItem();
        }

        //Starts the notification loading
        /*
        EMPlaylistService has the condition:
            !currentItem.getThumbnailUrl().equals(currentPlaylistItem.getThumbnailUrl())
        but for the first played item currentItem == currentPlaylistItem, which means the
        image isn't update
        */
        if (currentPlaylistItem != null) {
            int size = getResources().getDimensionPixelSize(com.devbrackets.android.exomedia.R.dimen.exomedia_big_notification_height);
            updateLargeNotificationImage(size, currentPlaylistItem);
        }

        //Starts the lock screen loading
        /*
        EMPlaylistService has the condition:
            !currentItem.getArtworkUrl().equalsIgnoreCase(currentPlaylistItem.getArtworkUrl())
        but for the first played item currentItem == currentPlaylistItem, which means the
        image isn't updated
        */
        if (currentPlaylistItem != null) {
            updateLockScreenArtwork(currentPlaylistItem);
        }

        postPlaylistItemChanged();
    }

    @Override
    //we use this to update the timestamp in the notification
    public boolean onProgressUpdated(EMMediaProgressEvent progressEvent) {
        boolean ret = super.onProgressUpdated(progressEvent);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                //UI can't keep up on real device
                //updateNotification();
                return null;
            }
        }.execute();
        return ret;
    }

    @Override
    /*
    Almost the same as in EMPlaylistService except that:
        -the title is the video title
        -the content is the timestamp
        -we pass the current position and audio duration to the notification
     */
    protected void updateNotification() {
        if (currentPlaylistItem == null || !foregroundSetup) {
            return;
        }

        //Generate the notification state
        EMNotification.NotificationMediaState mediaState = new EMNotification.NotificationMediaState();
        mediaState.setNextEnabled(getMediaPlaylistManager().isNextAvailable());
        mediaState.setPreviousEnabled(getMediaPlaylistManager().isPreviousAvailable());
        mediaState.setPlaying(isPlaying());


        //Update the big notification images
        Bitmap bitmap = getLargeNotificationImage();
        if (bitmap == null) {
            bitmap = getDefaultLargeNotificationImage();
        }

        Bitmap secondaryImage = getLargeNotificationSecondaryImage();
        if (secondaryImage == null) {
            secondaryImage = getDefaultLargeNotificationSecondaryImage();
        }

        String title = currentPlaylistItem.getTitle();
        String time = null;
        long position = -1, duration = -1;
        if (currentMediaProgress != null && currentMediaProgress.getDuration() != 0) {
            position = currentMediaProgress.getPosition();
            duration = currentMediaProgress.getDuration();
            time = String.format("%d:%02d/%d:%02d", TimeUnit.MILLISECONDS.toMinutes(position), TimeUnit.MILLISECONDS.toSeconds(position) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(position)), TimeUnit.MILLISECONDS.toMinutes(duration), TimeUnit.MILLISECONDS.toSeconds(duration) -
                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(duration)));
        }
        ((BYtNotification) notificationHelper).updateNotificationInformation(title, time, bitmap, secondaryImage, mediaState, position, duration);
    }
}

