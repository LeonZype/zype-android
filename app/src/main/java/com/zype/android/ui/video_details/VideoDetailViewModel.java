package com.zype.android.ui.video_details;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;

import com.squareup.otto.Subscribe;
import com.zype.android.DataRepository;
import com.zype.android.Db.DbHelper;
import com.zype.android.Db.Entity.Video;
import com.zype.android.ui.video_details.Model.VideoLiveData;
import com.zype.android.utils.Logger;
import com.zype.android.webapi.WebApiManager;
import com.zype.android.webapi.builder.VideoParamsBuilder;
import com.zype.android.webapi.events.video.VideoEvent;
import com.zype.android.webapi.model.video.VideoData;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Evgeny Cherkasov on 05.07.2018
 */
public class VideoDetailViewModel extends AndroidViewModel {
//    VideoLiveData video;
    VideoLiveData videoCheckOnAir;
    MutableLiveData<Video> videoLiveData;

    private Timer timer;
    private TimerTask timerTask;
    private long TIMER_PERIOD = 60000;

    private DataRepository repo;
    private WebApiManager api;

    public VideoDetailViewModel(Application application) {
        super(application);
        repo = DataRepository.getInstance(application);
        api = WebApiManager.getInstance();
        api.subscribe(this);
    }

    @Override
    protected void onCleared() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        api.unsubscribe(this);
        super.onCleared();
    }

    public LiveData<Video> getVideo(String videoId) {
        if (videoLiveData == null) {
            videoLiveData = new MutableLiveData<>();
            Video video = repo.getVideoSync(videoId);
            if (video != null) {
                videoLiveData.setValue(video);
            }
            loadVideo(videoId);
        }
        return videoLiveData;
    }

    public LiveData<Video> getVideo() {
        if (videoLiveData == null) {
            throw new IllegalStateException("Call `getVideo(videoId)` first to initialize video live data object");
        }
        return videoLiveData;
    }

    public void onVideoFinished(boolean isTrailer) {
        if (isTrailer) {
            // When trailer playback is finished just fire video detail event with existing data
            videoLiveData.setValue(videoLiveData.getValue());
        }
    }

    public VideoLiveData checkOnAir(final String videoId) {
        if (videoCheckOnAir == null) {
            videoCheckOnAir = new VideoLiveData();
            videoCheckOnAir.setCheckOnAir(true);
        }
        if (timer == null) {
            timer = new Timer();
        }
        else {
            timer.cancel();
            timer.purge();
        }
        timerTask = new TimerTask() {
            @Override
            public void run() {
                loadVideo(videoId);
            }
        };
        timer.schedule(timerTask, TIMER_PERIOD, TIMER_PERIOD);

        return videoCheckOnAir;
    }

    /**
     * Make API request for video with specified id
     *
     * @param videoId Video id
     */
    private void loadVideo(String videoId) {
        Logger.d("loadVideo(): videoId=" + videoId);
        VideoParamsBuilder builder = new VideoParamsBuilder()
                .addVideoId(videoId);
        WebApiManager.getInstance().executeRequest(WebApiManager.Request.VIDEO, builder.build());
    }

    /**
     * Handles API request for video
     *
     * @param event Response event
     */
    @Subscribe
    public void handleVideo(VideoEvent event) {
        Logger.d("handleVideo()");
        VideoData data = event.getEventData().getModelData().getVideoData();
        Video video = repo.getVideoSync(data.getId());
        if (video == null) {
            video = DbHelper.videoDataToVideoEntity(data);
        }
        else {
            video = DbHelper.updateVideoEntityByVideoData(video, data);
        }
        videoLiveData.setValue(video);
    }

    public boolean updateVideoOnAir(Video video) {
        Video dbVideo = repo.getVideoSync(video.id);
        if (dbVideo.onAir != video.onAir) {
            dbVideo.onAir = video.onAir;
            repo.updateVideo(dbVideo);
            return true;
        }
        else {
            return false;
        }
    }
}
