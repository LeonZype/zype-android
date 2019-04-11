package com.zype.android.ui.player;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.MutableLiveData;
import android.arch.lifecycle.Observer;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.google.android.exoplayer.TimeRange;
import com.google.android.exoplayer.chunk.Format;
import com.squareup.otto.Subscribe;
import com.zype.android.Auth.AuthHelper;
import com.zype.android.DataRepository;
import com.zype.android.Db.DbHelper;
import com.zype.android.Db.Entity.AdSchedule;
import com.zype.android.Db.Entity.AnalyticBeacon;
import com.zype.android.Db.Entity.Video;
import com.zype.android.ZypeApp;
import com.zype.android.ZypeConfiguration;
import com.zype.android.core.settings.SettingsProvider;
import com.zype.android.utils.Logger;
import com.zype.android.webapi.WebApiManager;
import com.zype.android.webapi.builder.ParamsBuilder;
import com.zype.android.webapi.builder.PlayerParamsBuilder;
import com.zype.android.webapi.events.player.PlayerAudioEvent;
import com.zype.android.webapi.events.player.PlayerVideoEvent;
import com.zype.android.webapi.model.player.Advertising;
import com.zype.android.webapi.model.player.Analytics;
import com.zype.android.webapi.model.player.File;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by Evgeny Cherkasov on 23.07.2018
 */
public class PlayerViewModel extends AndroidViewModel implements CustomPlayer.InfoListener {

    private MutableLiveData<List<PlayerMode>> availablePlayerModes;
    private MutableLiveData<String> contentUri = new MutableLiveData<>();
    private MutableLiveData<PlayerMode> playerMode;
    private MutableLiveData<String> playerUrl;

    private String videoId;
    private String playlistId;

    private List<AdSchedule> adSchedule;
    private AnalyticBeacon analyticBeacon;

    private long playbackPosition = 0;
    private boolean playTrailer = false;
    private boolean isPlaybackPositionRestored = false;
    private boolean isUrlLoaded = false;

    public enum PlayerMode {
        AUDIO,
        VIDEO
    }

    DataRepository repo;
    WebApiManager api;

    public PlayerViewModel(Application application) {
        super(application);
        repo = DataRepository.getInstance(application);
        api = WebApiManager.getInstance();
        api.subscribe(this);

        availablePlayerModes = new MutableLiveData<>();
        availablePlayerModes.setValue(new ArrayList<PlayerMode>());
        playerMode = new MutableLiveData<>();
    }

    @Override
    protected void onCleared() {
        api.unsubscribe(this);
        super.onCleared();
    }

    public PlayerViewModel setVideoId(String videoId) {
        this.videoId = videoId;
        return this;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getPlaylistId() {
        return playlistId;
    }

    public PlayerViewModel setPlaylistId(String playlistId) {
        this.playlistId = playlistId;
        return this;
    }

    public void initialize() {
        Video video = repo.getVideoSync(videoId);
        playbackPosition = video.playTime;
        isPlaybackPositionRestored = false;

        playTrailer = false;

        updateAvailablePlayerModes();
        setDefaultPlayerMode();

        loadVideoPlayerUrl(videoId);
        loadAudioPlayerUrl(videoId);
    }

    // Content Uri

    public MutableLiveData<String> getContentUri() {
        Video video = repo.getVideoSync(videoId);
        if (!isUrlLoaded) {
            video.playerAudioUrl = null;
            video.playerVideoUrl = null;
        }
        updateContentUri(video);
        return contentUri;
    }

    private void updateContentUri(Video video) {
        String newContentUri = null;
        if (playerMode.getValue() != null) {
            switch (playerMode.getValue()) {
                case AUDIO:
                    if (video.isDownloadedAudio == 1) {
                        newContentUri = video.downloadAudioPath;
                    }
                    else {
                        newContentUri = video.playerAudioUrl;
                    }
                    break;
                case VIDEO:
                    if (video.isDownloadedVideo == 1) {
                        newContentUri = video.downloadVideoPath;
                    }
                    else {
                        newContentUri = video.playerVideoUrl;
                    }
                    break;
            }
        }
        updateAdSchedule();
        updateAnalyticsBeacon();
        if (contentUri.getValue() == null) {
            if (newContentUri != null) {
                contentUri.setValue(newContentUri);
            }
        }
        else {
            if (!contentUri.getValue().equals(newContentUri)) {
                contentUri.setValue(newContentUri);
            }
        }
    }

    // Player mode

    public MutableLiveData<PlayerMode> getPlayerMode() {
        return playerMode;
    }

    public void setPlayerMode(PlayerMode mode) {
        Video video = repo.getVideoSync(videoId);
        if (video != null) {
            playerMode.setValue(mode);
            // TODO: Remove
            updatePlayerUrl(video);
        }
    }

    private void setDefaultPlayerMode() {
        List<PlayerMode> playerModes = availablePlayerModes.getValue();
        if (playerModes != null) {
            if (playerModes.contains(PlayerMode.VIDEO)) {
                playerMode.setValue(PlayerMode.VIDEO);
            }
            else if (playerModes.contains(PlayerMode.AUDIO)) {
                playerMode.setValue(PlayerMode.AUDIO);
            }
            else {
                playerMode.setValue(null);
            }
        }
        else {
            playerMode.setValue(null);
        }
    }

    public MutableLiveData<List<PlayerMode>> getAvailablePlayerModes() {
        return availablePlayerModes;
    }

    private void updateAvailablePlayerModes() {
        List<PlayerMode> result = new ArrayList<>();

        Video video = repo.getVideoSync(videoId);
        if (video != null) {
            if (ZypeApp.get(getApplication()).getAppConfiguration().audioOnlyPlaybackEnabled) {
                if (!TextUtils.isEmpty(video.playerAudioUrl)
                        || video.isDownloadedAudio == 1) {
                    result.add(PlayerMode.AUDIO);
                }
            }
            if (!TextUtils.isEmpty(video.playerVideoUrl)
                    || video.isDownloadedVideo == 1) {
                result.add(PlayerMode.VIDEO);
            }
        }

        availablePlayerModes.setValue(result);
    }

    // Playback position

    public long getPlaybackPosition() {
        return playbackPosition;
    }

    public void savePlaybackPosition(long position) {
        this.playbackPosition = position;

//        Video video = repo.getVideoSync(videoId);
//        video.playTime = position;
//        repo.updateVideo(video);

        isPlaybackPositionRestored = false;
    }

    public boolean playbackPositionRestored() {
        return isPlaybackPositionRestored;
    }

    public void onPlaybackPositionRestored() {
        isPlaybackPositionRestored = true;
    }


    // Ad schedule

    private void updateAdSchedule() {
        adSchedule = repo.getAdScheduleSync(videoId);
    }

    public List<AdSchedule> getAdSchedule() {
        return adSchedule;
    }

    // Analytics beacon

    private void updateAnalyticsBeacon() {
        analyticBeacon = repo.getAnalyticsBeaconSync(videoId);
    }

    public AnalyticBeacon getAnalyticBeacon() {
        return analyticBeacon;
    }

    // Trailer

    public boolean isPlayTrailer() {
        return playTrailer;
    }

    public void setTrailerVideoId(String trailerVideoId) {
        if (TextUtils.isEmpty(trailerVideoId)) {
            playTrailer = false;
            updateContentUri(repo.getVideoSync(videoId));
        }
        else {
            playTrailer = true;
            loadVideoPlayerUrl(trailerVideoId);
        }
    }

    /* Deprecated */

    public void init(String videoId, PlayerMode mediaType) {
        this.videoId = videoId;

        updateAvailablePlayerModes();
        if (mediaType != null || isMediaTypeAvailable(mediaType)) {
            playerMode.setValue(mediaType);
        }
        else {
            List<PlayerMode> mediaTypes = availablePlayerModes.getValue();
            if (mediaTypes != null && !mediaTypes.isEmpty()) {
                playerMode.setValue(mediaTypes.get(0));
            }
            else {
                playerMode.setValue(null);
            }
        }
    }

    public MutableLiveData<String> getPlayerUrl() {
        if (playerUrl == null) {
            playerUrl = new MutableLiveData<>();
        }
        Video video = repo.getVideoSync(videoId);
        if (ZypeApp.get(getApplication()).getAppConfiguration().audioOnlyPlaybackEnabled) {
            if (TextUtils.isEmpty(video.playerAudioUrl)) {
                loadAudioPlayerUrl(videoId);
            }
        }
        if (TextUtils.isEmpty(video.playerVideoUrl)) {
//                    loadVideoPlayerUrl(videoId);
        }
        updatePlayerUrl(video);
        return playerUrl;
    }

    private void updatePlayerUrl(Video video) {
        if (playerMode.getValue() != null) {
            switch (playerMode.getValue()) {
                case AUDIO:
                    if (video.isDownloadedAudio == 1) {
                        playerUrl.setValue(video.downloadAudioPath);
                    }
                    else {
                        playerUrl.setValue(video.playerAudioUrl);
                    }
                    break;
                case VIDEO:
                    if (video.isDownloadedVideo == 1) {
                        playerUrl.setValue(video.downloadVideoPath);
                    }
                    else {
                        playerUrl.setValue(video.playerVideoUrl);
                    }
                    break;
            }
        }
    }

    public boolean isMediaTypeAvailable(PlayerMode mediaType) {
        return availablePlayerModes.getValue() != null
                && availablePlayerModes.getValue().contains(mediaType);
    }

    public boolean isAudioDownloaded() {
        Video video = repo.getVideoSync(videoId);
        if (video != null) {
            return video.isDownloadedAudio == 1;
        }
        else
            return false;
    }

    public boolean isVideoDownloaded() {
        Video video = repo.getVideoSync(videoId);
        if (video != null) {
            return video.isDownloadedVideo == 1;
        }
        else
            return false;
    }

    public boolean isBackgroundPlaybackEnabled() {
        if (playerMode.getValue() != null) {
            switch (playerMode.getValue()) {
                case AUDIO:
                    return ZypeConfiguration.isBackgroundAudioPlaybackEnabled(getApplication());
                case VIDEO:
                    return ZypeConfiguration.isBackgroundPlaybackEnabled(getApplication());
                default:
                    return false;
            }
        }
        else {
            return false;
        }
    }

    // API calls

    /**
     * Make API request for audio player
     *
     * @param videoId Video id
     */
    private void loadAudioPlayerUrl(final String videoId) {
        Logger.d("loadAudioPlayerUrl(): videoId=" + videoId);

        if (AuthHelper.isLoggedIn()) {
            AuthHelper.onLoggedIn(new Observer<Boolean>() {
                @Override
                public void onChanged(@Nullable Boolean isLoggedIn) {
                    PlayerParamsBuilder builder = new PlayerParamsBuilder();
                    if (isLoggedIn) {
                        builder.addAccessToken();
                    }
                    else {
                        builder.addAppKey();
                    }
                    builder.addVideoId(videoId);
                    builder.addAudio();
                    api.executeRequest(WebApiManager.Request.PLAYER_AUDIO, builder.build());
                }
            });
        }
        else {
            PlayerParamsBuilder builder = new PlayerParamsBuilder();
            builder.addAppKey();
            builder.addVideoId(videoId);
            builder.addAudio();
            api.executeRequest(WebApiManager.Request.PLAYER_AUDIO, builder.build());
        }
    }

    /**
     * Handles API request for audio player
     *
     * @param event Response event
     */
    @Subscribe
    public void handleAudioPlayerUrl(PlayerAudioEvent event) {
        Logger.d("handleAudioPlayerUrl()");

        Bundle requestOptions = event.getOptions();
        HashMap<String, String> pathParams = (HashMap<String, String>) requestOptions.getSerializable(ParamsBuilder.GET_PARAMS);
        String videoId = pathParams.get(PlayerParamsBuilder.VIDEO_ID);

        List<File> files = event.getEventData().getModelData().getResponse().getBody().getFiles();
        if (files == null || files.isEmpty()) {
            Logger.w("handleAudioPlayerUrl(): Audio source not found");
            return;
        }

        // Take first source in the list
        String url = files.get(0).getUrl();
        Logger.d("handleAudioPlayerUrl(): url=" + url);

        // Save audio url in the local database if it was changed
        Video video = repo.getVideoSync(videoId);
        if (video.playerAudioUrl == null || !video.playerAudioUrl.equals(url)) {
            video.playerAudioUrl = url;
            repo.updateVideo(video);

            isUrlLoaded = true;

            updateAvailablePlayerModes();
            if (playerMode.getValue() == null) {
                playerMode.setValue(PlayerMode.AUDIO);
            }
            updateContentUri(video);
            // TODO: Remove when stop supporting exoplayer 1.5
            if (playerMode.getValue() == PlayerMode.AUDIO) {
                playerUrl.setValue(url);
            }
        }
    }

    /**
     * Make API request for video player
     *
     * @param videoId Video id
     */
    private void loadVideoPlayerUrl(final String videoId) {
        Logger.d("loadVideoPlayerUrl(): videoId=" + videoId);

        final PlayerParamsBuilder builder = new PlayerParamsBuilder();
        builder.addVideoId(videoId);
        if (playTrailer) {
            builder.addAppKey();
            api.executeRequest(WebApiManager.Request.PLAYER_VIDEO, builder.build());
        }
        else {
            if (AuthHelper.isLoggedIn()) {
                AuthHelper.onLoggedIn(new Observer<Boolean>() {
                    @Override
                    public void onChanged(@Nullable Boolean isLoggedIn) {
                        if (isLoggedIn) {
                            builder.addAccessToken();
                        }
                        else {
                            builder.addAppKey();
                        }
                        api.executeRequest(WebApiManager.Request.PLAYER_VIDEO, builder.build());
                    }
                });
            }
            else {
                builder.addAppKey();
                api.executeRequest(WebApiManager.Request.PLAYER_VIDEO, builder.build());
            }
        }
    }

    /**
     * Handles API request for video player
     *
     * @param event Response event
     */
    @Subscribe
    public void handleVideoPlayerUrl(PlayerVideoEvent event) {
        List<File> files = event.getEventData().getModelData().getResponse().getBody().getFiles();
        if (files == null || files.isEmpty()) {
            Logger.w("handleVideoPlayerUrl(): Video source not found");
            return;
        }

        // Take first source in the list
        String url = files.get(0).getUrl();
        Logger.d("handleVideoPlayerUrl(): url=" + url);

        // In play trailer mode just update content uri
        if (playTrailer) {
            contentUri.setValue(url);
            return;
        }

        // Ad tags
        repo.deleteAdSchedule(videoId);
        Advertising advertising = event.getEventData().getModelData().getResponse().getBody().getAdvertising();
        if (advertising != null) {
            List<AdSchedule> schedule = DbHelper.adScheduleDataToEntity(advertising.getSchedule(), videoId);
            repo.insertAdSchedule(schedule);
        }
        updateAdSchedule();

        // Analytics
        repo.deleteAnalyticsBeacon(videoId);
        Analytics analytics = event.getEventData().getModelData().getResponse().getBody().getAnalytics();
        if (analytics != null &&
                analytics.getBeacon() != null && analytics.getDimensions() != null) {
            repo.insertAnalyticsBeacon(DbHelper.analyticsToEntity(analytics));
        }
        updateAnalyticsBeacon();

        // Save video url in the local database if it was changed
        Video video = repo.getVideoSync(videoId);
        if (video.playerVideoUrl == null || !video.playerVideoUrl.equals(url)) {
            video.playerVideoUrl = url;
            repo.updateVideo(video);

            isUrlLoaded = true;

            updateAvailablePlayerModes();
            if (playerMode.getValue() == null) {
                playerMode.setValue(PlayerMode.VIDEO);
            }
            updateContentUri(video);
        }
    }

    //
    // 'CustomPlayer.InfoListener' implementation
    //

    @Override
    public void onVideoFormatEnabled(Format format, int trigger, long mediaTimeMs) {
        Logger.i("onVideoFormatEnabled()");
        if (getPlayerMode().getValue() == PlayerMode.VIDEO && format.codecs != null && format.codecs.equals("mp4a.40.2")) {
            Video video = repo.getVideoSync(videoId);
            video.playerVideoUrl = null;
            repo.updateVideo(video);

            updateAvailablePlayerModes();
            setPlayerMode(PlayerMode.AUDIO);
        }
    }

    @Override
    public void onAudioFormatEnabled(Format format, int trigger, long mediaTimeMs) {
        Logger.i("onAudioFormatEnabled()");
    }

    @Override
    public void onDroppedFrames(int count, long elapsed) {
    }

    @Override
    public void onBandwidthSample(int elapsedMs, long bytes, long bitrateEstimate) {
    }

    @Override
    public void onLoadStarted(int sourceId, long length, int type, int trigger, Format format, long mediaStartTimeMs, long mediaEndTimeMs) {
    }

    @Override
    public void onLoadCompleted(int sourceId, long bytesLoaded, int type, int trigger, Format format, long mediaStartTimeMs, long mediaEndTimeMs, long elapsedRealtimeMs, long loadDurationMs) {
    }

    @Override
    public void onDecoderInitialized(String decoderName, long elapsedRealtimeMs, long initializationDurationMs) {
    }

    @Override
    public void onAvailableRangeChanged(TimeRange availableRange) {
    }
}
