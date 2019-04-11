package com.zype.android.ui.video_details.fragments.summary;


import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.ns.developer.tagview.widget.TagCloudLinkView;
import com.zype.android.Db.Entity.Video;
import com.zype.android.R;
import com.zype.android.core.provider.CursorHelper;
import com.zype.android.core.provider.helpers.VideoHelper;
import com.zype.android.ui.base.BaseFragment;
import com.zype.android.ui.player.PlayerViewModel;
import com.zype.android.ui.video_details.VideoDetailViewModel;
import com.zype.android.utils.Logger;
import com.zype.android.webapi.model.video.VideoData;

import java.util.List;

public class SummaryFragment extends BaseFragment {
    private static final String ARG_VIDEO_ID = "video_id";
    private String videoId;

    private VideoDetailViewModel model;
    private PlayerViewModel playerViewModel;
    Observer<Video> videoDetailObserver;

    public SummaryFragment() {
    }

    public static SummaryFragment newInstance(String videoId) {
        SummaryFragment fragment = new SummaryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_VIDEO_ID, videoId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            videoId = getArguments().getString(ARG_VIDEO_ID);
        }
        else {
            throw new IllegalStateException("VideoId can not be empty");
        }

        initialize();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        final View view = inflater.inflate(R.layout.fragment_summary, container, false);
//        Cursor cursor = CursorHelper.getVideoCursor(getActivity().getContentResolver(), videoId);
//        if (cursor != null) {
//            if (cursor.moveToFirst()) {
//                VideoData video = VideoHelper.objectFromCursor(cursor);
//                ((TextView) view.findViewById(R.id.textVideoTitle)).setText(video.getTitle());
//                ((TextView) view.findViewById(R.id.textVideoDescription)).setText(video.getDescription());
////                TagCloudLinkView tagCloudView = (TagCloudLinkView) view.findViewById(R.id.tag_cloud);
//               //hide keywords
//               /* if (video.getKeywords() != null) {
//                    for (int i = 0; i < video.getKeywords().size(); i++) {
//                        tagCloudView.add(new Tag(i, video.getKeywords().get(i)));
//                    }
//                    tagCloudView.drawTags();
//                    tagCloudView.setOnTagSelectListener(new TagCloudLinkView.OnTagSelectListener() {
//                        @Override
//                        public void onTagSelected(Tag tag, int i) {
////                            UiUtils.showWarningSnackbar(view, tag.getText());
//                        }
//                    });
//                }*/
//            } else {
//                throw new IllegalStateException("DB not contains video with ID=" + videoId);
//            }
//            cursor.close();
//        }

        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        model = ViewModelProviders.of(getActivity()).get(VideoDetailViewModel.class);
        model.getVideo(videoId).observe(this, videoDetailObserver);

        playerViewModel = ViewModelProviders.of(getActivity()).get(PlayerViewModel.class);
    }

    @Override
    protected String getFragmentName() {
        return getString(R.string.fragment_name_summary);
    }

    private void initialize() {
        if (videoDetailObserver == null) {
            videoDetailObserver = createVideoDetailObserver();
        }
    }

    private Observer<Video> createVideoDetailObserver() {
        return new Observer<Video>() {
            @Override
            public void onChanged(final Video video) {
                Logger.d("getVideo(): onChanged()");
                ((TextView) getView().findViewById(R.id.textVideoTitle)).setText(video.getTitle());
                ((TextView) getView().findViewById(R.id.textVideoDescription)).setText(video.description);

                Button buttonPlayTrailer = getView().findViewById(R.id.buttonPlayTrailer);
                final List<String> previewIds = VideoHelper.getPreviewIdsList(video);
                if (previewIds.isEmpty()) {
                    buttonPlayTrailer.setVisibility(View.GONE);
                }
                else {
                    buttonPlayTrailer.setVisibility(View.VISIBLE);
                    buttonPlayTrailer.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            playTrailer(previewIds.get(0));
                        }
                    });
                }
            }
        };
    }

    private void playTrailer(String previewId) {
        Logger.d("playTrailer(): previewId = " + previewId);
        playerViewModel.setTrailerVideoId(previewId);
    }
}
