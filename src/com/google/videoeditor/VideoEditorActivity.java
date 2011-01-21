/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.videoeditor;

import java.util.ArrayList;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.media.videoeditor.MediaItem;
import android.media.videoeditor.MediaProperties;
import android.media.videoeditor.VideoEditor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import com.google.videoeditor.service.ApiService;
import com.google.videoeditor.service.MovieMediaItem;
import com.google.videoeditor.service.VideoEditorProject;
import com.google.videoeditor.util.FileUtils;
import com.google.videoeditor.util.MediaItemUtils;
import com.google.videoeditor.util.StringUtils;
import com.google.videoeditor.widgets.AudioTrackLinearLayout;
import com.google.videoeditor.widgets.MediaLinearLayout;
import com.google.videoeditor.widgets.OverlayLinearLayout;
import com.google.videoeditor.widgets.PlayheadView;
import com.google.videoeditor.widgets.PreviewSurfaceView;
import com.google.videoeditor.widgets.ScrollViewListener;
import com.google.videoeditor.widgets.TimelineHorizontalScrollView;
import com.google.videoeditor.widgets.TimelineRelativeLayout;
import com.google.videoeditor.widgets.ZoomControl;

/**
 * This is the main activity of the video editor. It handles video editing of
 * a project.
 */
public class VideoEditorActivity extends VideoEditorBaseActivity
        implements SurfaceHolder.Callback {
    // Logging
    private static final String TAG = "VideoEditorActivity";

    // State keys
    private static final String STATE_INSERT_AFTER_MEDIA_ITEM_ID = "insert_after_media_item_id";

    // Menu ids
    private static final int MENU_IMPORT_IMAGE_ID = 2;
    private static final int MENU_IMPORT_VIDEO_ID = 3;
    private static final int MENU_IMPORT_AUDIO_ID = 4;
    private static final int MENU_CHANGE_ASPECT_RATIO_ID = 5;
    private static final int MENU_EDIT_PROJECT_NAME_ID = 6;
    private static final int MENU_DELETE_PROJECT_ID = 7;
    private static final int MENU_EXPORT_MOVIE_ID = 8;
    private static final int MENU_PLAY_EXPORTED_MOVIE = 9;
    private static final int MENU_SHARE_VIDEO = 10;

    // Dialog ids
    private static final int DIALOG_DELETE_PROJECT_ID = 1;
    private static final int DIALOG_EDIT_PROJECT_NAME_ID = 2;
    private static final int DIALOG_CHOOSE_ASPECT_RATIO_ID = 3;
    private static final int DIALOG_EXPORT_OPTIONS_ID = 4;

    public static final int DIALOG_REMOVE_MEDIA_ITEM_ID = 10;
    public static final int DIALOG_REMOVE_TRANSITION_ID = 11;
    public static final int DIALOG_CHANGE_RENDERING_MODE_ID = 12;
    public static final int DIALOG_REMOVE_OVERLAY_ID = 13;
    public static final int DIALOG_REMOVE_EFFECT_ID = 14;
    public static final int DIALOG_REMOVE_AUDIO_TRACK_ID = 15;

    // Dialog parameters
    private static final String PARAM_ASPECT_RATIOS_LIST = "aspect_ratios";
    private static final String PARAM_CURRENT_ASPECT_RATIO_INDEX = "current_aspect_ratio";

    // Request codes
    private static final int REQUEST_CODE_IMPORT_VIDEO = 1;
    private static final int REQUEST_CODE_IMPORT_IMAGE = 2;
    private static final int REQUEST_CODE_IMPORT_MUSIC = 3;

    public static final int REQUEST_CODE_EDIT_TRANSITION = 10;
    public static final int REQUEST_CODE_PICK_TRANSITION = 11;
    public static final int REQUEST_CODE_PICK_OVERLAY = 12;
    public static final int REQUEST_CODE_EDIT_OVERLAY = 13;
    public static final int REQUEST_CODE_PICK_EFFECT = 14;
    public static final int REQUEST_CODE_EDIT_EFFECT = 15;
    public static final int REQUEST_CODE_KEN_BURNS = 16;

    // The maximum zoom level
    private static final int MAX_ZOOM_LEVEL = 60;
    private static final int ZOOM_STEP = 2;

    private final TimelineRelativeLayout.LayoutCallback mLayoutCallback =
        new TimelineRelativeLayout.LayoutCallback() {
        /*
         * {@inheritDoc}
         */
        public void onLayoutComplete() {
            // Scroll the timeline such that the specified position
            // is in the center of the screen
            mTimelineScroller.appScrollTo(timeToDimension(mProject.getPlayheadPos()), true);
        }
    };

    // Instance variables
    private PreviewSurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private PreviewThread mPreviewThread;
    private View mEditorProjectView;
    private View mEditorEmptyView;
    private TimelineHorizontalScrollView mTimelineScroller;
    private TimelineRelativeLayout mTimelineLayout;
    private OverlayLinearLayout mOverlayLayout;
    private AudioTrackLinearLayout mAudioTrackLayout;
    private MediaLinearLayout mMediaLayout;
    private PlayheadView mPlayheadView;
    private TextView mTimeView;
    private ImageButton mPreviewPlayButton;
    private int mActivityWidth;
    private String mInsertMediaItemAfterMediaItemId;
    private long mCurrentPlayheadPosMs;
    private ProgressDialog mExportProgressDialog;
    private ZoomControl mZoomBar;

    // Variables used in onActivityResult
    private Uri mAddMediaItemVideoUri;
    private Uri mAddMediaItemImageUri;
    private Uri mAddAudioTrackUri;
    private String mAddTransitionAfterMediaId;
    private int mAddTransitionType;
    private long mAddTransitionDurationMs;
    private String mEditTransitionAfterMediaId, mEditTransitionId;
    private int mEditTransitionType;
    private long mEditTransitionDurationMs;
    private String mAddOverlayMediaItemId;
    private Bundle mAddOverlayUserAttributes;
    private String mEditOverlayMediaItemId;
    private String mEditOverlayId;
    private Bundle mEditOverlayUserAttributes;
    private String mAddEffectMediaItemId;
    private int mAddEffectType;
    private String mEditEffectMediaItemId;
    private int mEditEffectType;
    private String mSetKenBurnsMediaItemId;
    private Rect mSetKenBurnsStartRect;
    private Rect mSetKenBurnsEndRect;

    /*
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Prepare the surface holder
        mSurfaceView = (PreviewSurfaceView)findViewById(R.id.video_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.addCallback(this);
        mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mEditorProjectView = findViewById(R.id.editor_project_view);
        mEditorEmptyView = findViewById(R.id.empty_project_view);

        mTimelineScroller = (TimelineHorizontalScrollView)findViewById(R.id.timeline_scroller);
        mTimelineLayout = (TimelineRelativeLayout)findViewById(R.id.timeline);
        mMediaLayout = (MediaLinearLayout)findViewById(R.id.timeline_media);
        mOverlayLayout = (OverlayLinearLayout)findViewById(R.id.timeline_overlays);
        mAudioTrackLayout = (AudioTrackLinearLayout)findViewById(R.id.timeline_audio_tracks);
        mPlayheadView = (PlayheadView)findViewById(R.id.timeline_playhead);
        mPreviewPlayButton = (ImageButton)findViewById(R.id.editor_play);

        mTimeView = (TextView)findViewById(R.id.editor_time);

        getActionBar().setDisplayHomeAsUpEnabled(true);
        mMediaLayout.setListener(new MediaLinearLayout.MediaLayoutListener() {
            /*
             * {@inheritDoc}
             */
            public void onRequestScrollBy(int scrollBy, boolean smooth) {
                mTimelineScroller.appScrollBy(scrollBy, smooth);
            }

            /*
             * {@inheritDoc}
             */
            public void onRequestScrollToTime(long scrollToTime, boolean smooth) {
                movePlayhead(scrollToTime);
            }

            /*
             * {@inheritDoc}
             */
            public void onAddMediaItem(String afterMediaItemId) {
                mInsertMediaItemAfterMediaItemId = afterMediaItemId;
                final CharSequence[] items = {
                        VideoEditorActivity.this.getString(R.string.editor_import_image),
                        VideoEditorActivity.this.getString(R.string.editor_import_video)
                };

                final AlertDialog.Builder builder = new AlertDialog.Builder(
                        VideoEditorActivity.this);
                builder.setSingleChoiceItems(items, -1, new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int item) {
                        dialog.dismiss();

                        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                        if (item == 0) {
                            intent.setType("image/*");
                            startActivityForResult(intent, REQUEST_CODE_IMPORT_IMAGE);
                        } else {
                            intent.setType("video/*");
                            startActivityForResult(intent, REQUEST_CODE_IMPORT_VIDEO);
                        }
                    }
                });
                builder.show();
            }

            /*
             * {@inheritDoc}
             */
            public void onTrimMediaItem(MovieMediaItem mediaItem, long timeMs) {
                updateTimelineDuration();
                if (mProject != null && mPreviewThread != null && !mPreviewThread.isPlaying()) {
                    if (mediaItem.isVideoClip()) {
                        if (timeMs >= 0) {
                            mPreviewThread.renderMediaItemFrame(mediaItem, timeMs);
                        }
                    } else {
                        mPreviewThread.previewFrame(mProject,
                                mProject.getMediaItemBeginTime(mediaItem.getId())
                                + timeMs, mProject.getMediaItemCount() == 0);
                    }
                }
            }

            /*
             * {@inheritDoc}
             */
            public void onTrimMediaItemComplete(MovieMediaItem mediaItem, long timeMs) {
                // We need to repaint the timeline layout to clear the old
                // playhead position (the one drawn during trimming)
                mTimelineLayout.invalidate();
                showPreviewFrame();
            }
        });

        mAudioTrackLayout.setListener(new AudioTrackLinearLayout.AudioTracksLayoutListener() {
            /*
             * {@inheritDoc}
             */
            public void onAddAudioTrack() {
                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("audio/*");
                startActivityForResult(intent, REQUEST_CODE_IMPORT_MUSIC);
            }
        });

        mTimelineScroller.addScrollListener(new ScrollViewListener() {
            // Instance variables
            private int mActiveWidth;

            private long mDurationMs;

            private int mLastScrollX;

            /*
             * {@inheritDoc}
             */
            public void onScrollBegin(View view, int scrollX, int scrollY, boolean appScroll) {
                if (!appScroll && mProject != null) {
                    mActiveWidth = mMediaLayout.getWidth() - mActivityWidth;
                    mDurationMs = mProject.computeDuration();
                } else {
                    mActiveWidth = 0;
                }

                mLastScrollX = scrollX;
            }

            /*
             * {@inheritDoc}
             */
            public void onScrollProgress(View view, int scrollX, int scrollY, boolean appScroll) {
                // We check if the project is valid since the project may
                // close while scrolling
                if (!appScroll && mActiveWidth > 0 && mProject != null) {
                    final int deltaScrollX = Math.abs(mLastScrollX - scrollX);

                    if (deltaScrollX < 100) {
                        mLastScrollX = scrollX;
                        // When scrolling at high speed do not display the
                        // preview frame
                        final long timeMs = (scrollX * mDurationMs) / mActiveWidth;
                        if (setPlayhead(timeMs < 0 ? 0 : timeMs)) {
                            showPreviewFrame();
                        }
                    }
                }
            }

            /*
             * {@inheritDoc}
             */
            public void onScrollEnd(View view, int scrollX, int scrollY, boolean appScroll) {
                // We check if the project is valid since the project may
                // close while scrolling
                if (!appScroll && mActiveWidth > 0 && mProject != null && scrollX != mLastScrollX) {
                    final long timeMs = (scrollX * mDurationMs) / mActiveWidth;
                    if (setPlayhead(timeMs < 0 ? 0 : timeMs)) {
                        showPreviewFrame();
                    }
                }
            }
        });

        mTimelineScroller.setScaleListener(new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            // Guard against this many scale events in the opposite direction
            private static final int SCALE_TOLERANCE = 3;

            private int mLastScaleFactorSign;

            private float mLastScaleFactor;

            /*
             * {@inheritDoc}
             */
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                mLastScaleFactorSign = 0;
                return true;
            }

            /*
             * {@inheritDoc}
             */
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (mProject == null) {
                    return false;
                }

                final float scaleFactor = detector.getScaleFactor();
                final float deltaScaleFactor = scaleFactor - mLastScaleFactor;
                if (deltaScaleFactor > 0.01f || deltaScaleFactor < -0.01f) {
                    if (scaleFactor < 1.0f) {
                        if (mLastScaleFactorSign <= 0) {
                            zoomTimeline(mProject.getZoomLevel() - ZOOM_STEP, true);
                        }

                        if (mLastScaleFactorSign > -SCALE_TOLERANCE) {
                            mLastScaleFactorSign--;
                        }
                    } else if (scaleFactor > 1.0f) {
                        if (mLastScaleFactorSign >= 0) {
                            zoomTimeline(mProject.getZoomLevel() + ZOOM_STEP, true);
                        }

                        if (mLastScaleFactorSign < SCALE_TOLERANCE) {
                            mLastScaleFactorSign++;
                        }
                    }
                }

                mLastScaleFactor = scaleFactor;
                return true;
            }

            /*
             * {@inheritDoc}
             */
            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
            }
        });

        if (savedInstanceState != null) {
            mInsertMediaItemAfterMediaItemId = savedInstanceState.getString(
                    STATE_INSERT_AFTER_MEDIA_ITEM_ID);
        }

        // Compute the activity width
        final Display display = getWindowManager().getDefaultDisplay();
        mActivityWidth = display.getWidth();

        mSurfaceView.setGestureListener(new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    /*
                     * {@inheritDoc}
                     */
                    @Override
                    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                            float velocityY) {
                        if (mPreviewThread != null && mPreviewThread.isPlaying()) {
                            return false;
                        }

                        mTimelineScroller.fling(-(int)velocityX);
                        return true;
                    }

                    /*
                     * {@inheritDoc}
                     */
                    @Override
                    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,
                            float distanceY) {
                        if (mPreviewThread != null && mPreviewThread.isPlaying()) {
                            return false;
                        }

                        mTimelineScroller.scrollBy((int)distanceX, 0);
                        return true;
                    }
                }));

        mZoomBar = ((ZoomControl)findViewById(R.id.editor_zoom));
        mZoomBar.setMax(MAX_ZOOM_LEVEL);
        mZoomBar.setOnZoomChangeListener(new ZoomControl.OnZoomChangeListener() {
            /*
             * {@inheritDoc}
             */
            public void onProgressChanged(int progress, boolean fromUser) {
                if (mProject != null) {
                    zoomTimeline(progress, false);
                }
            }
        });
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();

        // Dismiss the export progress dialog. If the export will still be pending
        // when we return to this activity, we will display this dialog again.
        if (mExportProgressDialog != null) {
            mExportProgressDialog.dismiss();
            mExportProgressDialog = null;
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putString(STATE_INSERT_AFTER_MEDIA_ITEM_ID, mInsertMediaItemAfterMediaItemId);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(Menu.NONE, MENU_IMPORT_VIDEO_ID, Menu.NONE,
                R.string.editor_import_video).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_IMPORT_IMAGE_ID, Menu.NONE,
                R.string.editor_import_image).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        menu.add(Menu.NONE, MENU_IMPORT_AUDIO_ID, Menu.NONE, R.string.editor_import_audio);
        menu.add(Menu.NONE, MENU_CHANGE_ASPECT_RATIO_ID, Menu.NONE,
                R.string.editor_change_aspect_ratio);
        menu.add(Menu.NONE, MENU_EDIT_PROJECT_NAME_ID, Menu.NONE,
                R.string.editor_edit_project_name);
        menu.add(Menu.NONE, MENU_EXPORT_MOVIE_ID, Menu.NONE, R.string.editor_export_movie);
        menu.add(Menu.NONE, MENU_PLAY_EXPORTED_MOVIE, Menu.NONE,
                R.string.editor_play_exported_movie);
        menu.add(Menu.NONE, MENU_SHARE_VIDEO, Menu.NONE, R.string.editor_share_movie);
        menu.add(Menu.NONE, MENU_DELETE_PROJECT_ID, Menu.NONE, R.string.editor_delete_project);
        return true;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final boolean haveProject = mProject != null;
        final boolean haveMediaItems = haveProject && mProject.getMediaItemCount() > 0;
        menu.findItem(MENU_IMPORT_VIDEO_ID).setVisible(haveProject);
        menu.findItem(MENU_IMPORT_IMAGE_ID).setVisible(haveProject);
        menu.findItem(MENU_IMPORT_AUDIO_ID).setVisible(haveProject &&
                mProject.getAudioTracks().size() == 0 && haveMediaItems);
        menu.findItem(MENU_CHANGE_ASPECT_RATIO_ID).setVisible(haveProject &&
                mProject.hasMultipleAspectRatios());
        menu.findItem(MENU_EDIT_PROJECT_NAME_ID).setVisible(haveProject);
        menu.findItem(MENU_EXPORT_MOVIE_ID).setVisible(haveProject && haveMediaItems);
        menu.findItem(MENU_PLAY_EXPORTED_MOVIE).setVisible(haveProject &&
                mProject.getExportedMovieUri() != null);
        menu.findItem(MENU_SHARE_VIDEO).setVisible(haveProject &&
                mProject.getExportedMovieUri() != null);
        menu.findItem(MENU_DELETE_PROJECT_ID).setVisible(haveProject);
        return true;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                final Intent intent = new Intent(this, ProjectsActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);

                finish();
                return true;
            }

            case MENU_IMPORT_VIDEO_ID: {
                final MovieMediaItem mediaItem = mProject.getInsertAfterMediaItem(
                        mProject.getPlayheadPos());
                if (mediaItem != null) {
                    mInsertMediaItemAfterMediaItemId = mediaItem.getId();
                } else {
                    mInsertMediaItemAfterMediaItemId = null;
                }

                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("video/*");
                startActivityForResult(intent, REQUEST_CODE_IMPORT_VIDEO);
                return true;
            }

            case MENU_IMPORT_IMAGE_ID: {
                final MovieMediaItem mediaItem = mProject.getInsertAfterMediaItem(
                        mProject.getPlayheadPos());
                if (mediaItem != null) {
                    mInsertMediaItemAfterMediaItemId = mediaItem.getId();
                } else {
                    mInsertMediaItemAfterMediaItemId = null;
                }

                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                startActivityForResult(intent, REQUEST_CODE_IMPORT_IMAGE);
                return true;
            }

            case MENU_IMPORT_AUDIO_ID: {
                final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("audio/*");
                startActivityForResult(intent, REQUEST_CODE_IMPORT_MUSIC);
                return true;
            }

            case MENU_CHANGE_ASPECT_RATIO_ID: {
                final ArrayList<Integer> aspectRatiosList = mProject.getUniqueAspectRatiosList();
                final int size = aspectRatiosList.size();
                if (size > 1) {
                    final Bundle bundle = new Bundle();
                    bundle.putIntegerArrayList(PARAM_ASPECT_RATIOS_LIST, aspectRatiosList);

                    // Get the current aspect ratio index
                    final int currentAspectRatio = mProject.getAspectRatio();
                    int currentAspectRatioIndex = 0;
                    for (int i = 0; i < size; i++) {
                        final int aspectRatio = aspectRatiosList.get(i);
                        if (aspectRatio == currentAspectRatio) {
                            currentAspectRatioIndex = i;
                            break;
                        }
                    }
                    bundle.putInt(PARAM_CURRENT_ASPECT_RATIO_INDEX, currentAspectRatioIndex);
                    showDialog(DIALOG_CHOOSE_ASPECT_RATIO_ID, bundle);
                }
                return true;
            }

            case MENU_EDIT_PROJECT_NAME_ID: {
                showDialog(DIALOG_EDIT_PROJECT_NAME_ID);
                return true;
            }

            case MENU_DELETE_PROJECT_ID: {
                // Confirm project delete
                showDialog(DIALOG_DELETE_PROJECT_ID);
                return true;
            }

            case MENU_EXPORT_MOVIE_ID: {
                // Present the user with a dialog to choose export options
                showDialog(DIALOG_EXPORT_OPTIONS_ID);
                return true;
            }

            case MENU_PLAY_EXPORTED_MOVIE: {
                final Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(mProject.getExportedMovieUri(), "video/*");
                startActivity(intent);
                return true;
            }

            case MENU_SHARE_VIDEO: {
                final Intent intent = new Intent(Intent.ACTION_SEND,
                        mProject.getExportedMovieUri());
                intent.setType("video/*");
                startActivity(intent);
                return true;
            }

            default: {
                return false;
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    public Dialog onCreateDialog(int id, final Bundle bundle) {
        switch (id) {
            case DIALOG_CHOOSE_ASPECT_RATIO_ID: {
                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(getString(R.string.editor_change_aspect_ratio));
                final ArrayList<Integer> aspectRatios =
                    bundle.getIntegerArrayList(PARAM_ASPECT_RATIOS_LIST);
                final int count = aspectRatios.size();
                final CharSequence[] aspectRatioStrings = new CharSequence[count];
                for (int i = 0; i < count; i++) {
                    int aspectRatio = aspectRatios.get(i);
                    switch (aspectRatio) {
                        case MediaProperties.ASPECT_RATIO_11_9: {
                            aspectRatioStrings[i] = getString(R.string.aspect_ratio_11_9);
                            break;
                        }

                        case MediaProperties.ASPECT_RATIO_16_9: {
                            aspectRatioStrings[i] = getString(R.string.aspect_ratio_16_9);
                            break;
                        }

                        case MediaProperties.ASPECT_RATIO_3_2: {
                            aspectRatioStrings[i] = getString(R.string.aspect_ratio_3_2);
                            break;
                        }

                        case MediaProperties.ASPECT_RATIO_4_3: {
                            aspectRatioStrings[i] = getString(R.string.aspect_ratio_4_3);
                            break;
                        }

                        case MediaProperties.ASPECT_RATIO_5_3: {
                            aspectRatioStrings[i] = getString(R.string.aspect_ratio_5_3);
                            break;
                        }

                        default: {
                            break;
                        }
                    }
                }

                builder.setSingleChoiceItems(aspectRatioStrings,
                        bundle.getInt(PARAM_CURRENT_ASPECT_RATIO_INDEX),
                        new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        final int aspectRatio = aspectRatios.get(which);
                        ApiService.setAspectRatio(VideoEditorActivity.this, mProjectPath,
                                aspectRatio);

                        removeDialog(DIALOG_CHOOSE_ASPECT_RATIO_ID);
                    }
                });
                builder.setCancelable(true);
                builder.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        removeDialog(DIALOG_CHOOSE_ASPECT_RATIO_ID);
                    }
                });
                return builder.create();
            }

            case DIALOG_DELETE_PROJECT_ID: {
                return AlertDialogs.createAlert(this, getString(R.string.editor_delete_project), 0,
                                getString(R.string.editor_delete_project_question),
                                    getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        ApiService.deleteProject(VideoEditorActivity.this, mProjectPath);
                        mProjectPath = null;
                        mProject = null;
                        enterDisabledState(R.string.editor_no_project);

                        removeDialog(DIALOG_DELETE_PROJECT_ID);
                        finish();
                    }
                }, getString(R.string.no), new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        removeDialog(DIALOG_DELETE_PROJECT_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        removeDialog(DIALOG_DELETE_PROJECT_ID);
                    }
                }, true);
            }

            case DIALOG_DELETE_BAD_PROJECT_ID: {
                return AlertDialogs.createAlert(this, getString(R.string.editor_delete_project), 0,
                                getString(R.string.editor_load_error),
                                    getString(R.string.yes),
                        new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        ApiService.deleteProject(VideoEditorActivity.this,
                                bundle.getString(PARAM_PROJECT_PATH));

                        removeDialog(DIALOG_DELETE_BAD_PROJECT_ID);
                        finish();
                    }
                }, getString(R.string.no), new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        removeDialog(DIALOG_DELETE_BAD_PROJECT_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        removeDialog(DIALOG_DELETE_BAD_PROJECT_ID);
                    }
                }, true);
            }

            case DIALOG_EDIT_PROJECT_NAME_ID: {
                if (mProject == null) {
                    return null;
                }

                return AlertDialogs.createEditDialog(this,
                    getString(R.string.editor_edit_project_name),
                    mProject.getName(), getString(android.R.string.ok),
                    new DialogInterface.OnClickListener() {
                        /*
                         * {@inheritDoc}
                         */
                        public void onClick(DialogInterface dialog, int which) {
                            final TextView tv =
                                (TextView)((AlertDialog)dialog).findViewById(R.id.text_1);
                            mProject.setProjectName(tv.getText().toString());
                            getActionBar().setTitle(tv.getText());
                            removeDialog(DIALOG_EDIT_PROJECT_NAME_ID);
                        }
                    }, getString(android.R.string.cancel),
                    new DialogInterface.OnClickListener() {
                        /*
                         * {@inheritDoc}
                         */
                        public void onClick(DialogInterface dialog, int which) {
                            removeDialog(DIALOG_EDIT_PROJECT_NAME_ID);
                        }
                    }, new DialogInterface.OnCancelListener() {
                        /*
                         * {@inheritDoc}
                         */
                        public void onCancel(DialogInterface dialog) {
                            removeDialog(DIALOG_EDIT_PROJECT_NAME_ID);
                        }
                    }, InputType.TYPE_NULL, 32);
            }

            case DIALOG_EXPORT_OPTIONS_ID: {
                if (mProject == null) {
                    return null;
                }

                return ExportOptionsDialog.create(this,
                        new ExportOptionsDialog.ExportOptionsListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onExportOptions(int movieHeight, int movieBitrate) {
                        mPendingExportFilename = FileUtils.createMovieName(
                                MediaProperties.FILE_MP4);
                        ApiService.exportVideoEditor(VideoEditorActivity.this, mProjectPath,
                                mPendingExportFilename, movieHeight, movieBitrate);

                        removeDialog(DIALOG_EXPORT_OPTIONS_ID);

                        showExportProgress();
                    }
                }, new DialogInterface.OnClickListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onClick(DialogInterface dialog, int which) {
                        removeDialog(DIALOG_EXPORT_OPTIONS_ID);
                    }
                }, new DialogInterface.OnCancelListener() {
                    /*
                     * {@inheritDoc}
                     */
                    public void onCancel(DialogInterface dialog) {
                        removeDialog(DIALOG_EXPORT_OPTIONS_ID);
                    }
                }, mProject.getAspectRatio());
            }

            case DIALOG_REMOVE_MEDIA_ITEM_ID: {
                return mMediaLayout.onCreateDialog(id, bundle);
            }

            case DIALOG_CHANGE_RENDERING_MODE_ID: {
                return mMediaLayout.onCreateDialog(id, bundle);
            }

            case DIALOG_REMOVE_TRANSITION_ID: {
                return mMediaLayout.onCreateDialog(id, bundle);
            }

            case DIALOG_REMOVE_OVERLAY_ID: {
                return mOverlayLayout.onCreateDialog(id, bundle);
            }

            case DIALOG_REMOVE_EFFECT_ID: {
                return mMediaLayout.onCreateDialog(id, bundle);
            }

            case DIALOG_REMOVE_AUDIO_TRACK_ID: {
                return mAudioTrackLayout.onCreateDialog(id, bundle);
            }

            default: {
                return null;
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    public void onClickHandler(View target) {
        final long playheadPosMs = mProject.getPlayheadPos();

        switch (target.getId()) {
            case R.id.editor_play: {
                if (mProject != null && mPreviewThread != null) {
                    if (mPreviewThread.isPlaying()) {
                        mPreviewThread.stopPreviewPlayback();
                    } else if (mProject.getMediaItemCount() > 0){
                        mPreviewThread.startPreviewPlayback(mProject, playheadPosMs);
                    }
                }
                break;
            }

            case R.id.editor_rewind: {
                if (mProject != null && mPreviewThread != null) {
                    mPreviewThread.stopPreviewPlayback();

                    movePlayhead(0);
                    showPreviewFrame();
                }
                break;
            }

            case R.id.editor_next: {
                if (mProject != null && mPreviewThread != null) {
                    mPreviewThread.stopPreviewPlayback();

                    final MovieMediaItem mediaItem = mProject.getNextMediaItem(playheadPosMs);
                    if (mediaItem != null) {
                        movePlayhead(mProject.getMediaItemBeginTime(mediaItem.getId()));
                    } else { // Move to the end of the timeline
                        movePlayhead(mProject.computeDuration());
                    }

                    showPreviewFrame();
                }
                break;
            }

            case R.id.editor_prev: {
                if (mProject != null && mPreviewThread != null) {
                    mPreviewThread.stopPreviewPlayback();

                    final MovieMediaItem mediaItem = mProject.getPreviousMediaItem(playheadPosMs);
                    if (mediaItem != null) {
                        movePlayhead(mProject.getMediaItemBeginTime(mediaItem.getId()));
                    } else { // Move to the beginning of the timeline
                        movePlayhead(0);
                    }

                    showPreviewFrame();
                }
                break;
            }

            default: {
                break;
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent extras) {
        super.onActivityResult(requestCode, resultCode, extras);
        if (resultCode == RESULT_CANCELED) {
            return;
        }

        switch (requestCode) {
            case REQUEST_CODE_IMPORT_VIDEO: {
                final Uri mediaUri = extras.getData();
                if (mProject != null) {
                    ApiService.addMediaItemVideoUri(this, mProjectPath, ApiService.generateId(),
                            mInsertMediaItemAfterMediaItemId,
                            mediaUri, MediaItem.RENDERING_MODE_BLACK_BORDER, mProject.getTheme());
                    mInsertMediaItemAfterMediaItemId = null;
                } else {
                    // Add this video after the project loads
                    mAddMediaItemVideoUri = mediaUri;
                }
                break;
            }

            case REQUEST_CODE_IMPORT_IMAGE: {
                final Uri mediaUri = extras.getData();
                if (mProject != null) {
                    ApiService.addMediaItemImageUri(this, mProjectPath, ApiService.generateId(),
                            mInsertMediaItemAfterMediaItemId,
                            mediaUri, MediaItem.RENDERING_MODE_BLACK_BORDER,
                            MediaItemUtils.getDefaultImageDuration(), mProject.getTheme());
                    mInsertMediaItemAfterMediaItemId = null;
                } else {
                    // Add this image after the project loads
                    mAddMediaItemImageUri = mediaUri;
                }
                break;
            }

            case REQUEST_CODE_IMPORT_MUSIC: {
                final Uri data = extras.getData();
                if (mProject != null) {
                    ApiService.addAudioTrack(this, mProjectPath, ApiService.generateId(), data,
                            true);
                } else {
                    mAddAudioTrackUri = data;
                }
                break;
            }

            case REQUEST_CODE_EDIT_TRANSITION: {
                final int type = extras.getIntExtra(TransitionsActivity.PARAM_TRANSITION_TYPE, -1);
                final String afterMediaId = extras.getStringExtra(
                        TransitionsActivity.PARAM_AFTER_MEDIA_ITEM_ID);
                final String transitionId = extras.getStringExtra(
                        TransitionsActivity.PARAM_TRANSITION_ID);
                final long transitionDurationMs = extras.getLongExtra(
                        TransitionsActivity.PARAM_TRANSITION_DURATION, 500);
                if (mProject != null) {
                    mMediaLayout.editTransition(afterMediaId, transitionId, type,
                            transitionDurationMs);
                } else {
                    // Add this transition after you load the project
                    mEditTransitionAfterMediaId = afterMediaId;
                    mEditTransitionId = transitionId;
                    mEditTransitionType = type;
                    mEditTransitionDurationMs = transitionDurationMs;
                }
                break;
            }

            case REQUEST_CODE_PICK_TRANSITION: {
                final int type = extras.getIntExtra(TransitionsActivity.PARAM_TRANSITION_TYPE, -1);
                final String afterMediaId = extras.getStringExtra(
                        TransitionsActivity.PARAM_AFTER_MEDIA_ITEM_ID);
                final long transitionDurationMs = extras.getLongExtra(
                        TransitionsActivity.PARAM_TRANSITION_DURATION, 500);
                if (mProject != null) {
                    mMediaLayout.addTransition(afterMediaId, type, transitionDurationMs);
                } else {
                    // Add this transition after you load the project
                    mAddTransitionAfterMediaId = afterMediaId;
                    mAddTransitionType = type;
                    mAddTransitionDurationMs = transitionDurationMs;
                }
                break;
            }

            case REQUEST_CODE_PICK_OVERLAY: {
                final String mediaItemId =
                    extras.getStringExtra(OverlayTitleActivity.PARAM_MEDIA_ITEM_ID);
                final Bundle bundle =
                    extras.getBundleExtra(OverlayTitleActivity.PARAM_OVERLAY_ATTRIBUTES);
                if (mProject != null) {
                    final MovieMediaItem mediaItem = mProject.getMediaItem(mediaItemId);
                    if (mediaItem != null) {
                        ApiService.addOverlay(this, mProject.getPath(), mediaItemId,
                                ApiService.generateId(), bundle,
                                mediaItem.getAppBoundaryBeginTime(),
                                OverlayLinearLayout.DEFAULT_TITLE_DURATION);
                    }
                } else {
                    // Add this overlay after you load the project
                    mAddOverlayMediaItemId = mediaItemId;
                    mAddOverlayUserAttributes = bundle;
                }
                break;
            }

            case REQUEST_CODE_EDIT_OVERLAY: {
                final Bundle bundle =
                    extras.getBundleExtra(OverlayTitleActivity.PARAM_OVERLAY_ATTRIBUTES);
                final String mediaItemId =
                    extras.getStringExtra(OverlayTitleActivity.PARAM_MEDIA_ITEM_ID);
                final String overlayId =
                    extras.getStringExtra(OverlayTitleActivity.PARAM_OVERLAY_ID);
                if (mProject != null) {
                    ApiService.setOverlayUserAttributes(this, mProject.getPath(), mediaItemId,
                            overlayId, bundle);
                } else {
                    // Edit this overlay after you load the project
                    mEditOverlayMediaItemId = mediaItemId;
                    mEditOverlayId = overlayId;
                    mEditOverlayUserAttributes = bundle;
                }
                break;
            }

            case REQUEST_CODE_PICK_EFFECT: {
                final String mediaItemId =
                    extras.getStringExtra(EffectsActivity.PARAM_MEDIA_ITEM_ID);
                final int type = extras.getIntExtra(EffectsActivity.PARAM_EFFECT_TYPE,
                        EffectType.EFFECT_COLOR_GRADIENT);
                if (mProject != null) {
                    mMediaLayout.addEffect(type, mediaItemId);
                } else {
                    // Add this overlay after you load the project
                    mAddEffectMediaItemId = mediaItemId;
                    mAddEffectType = type;
                }
                break;
            }

            case REQUEST_CODE_EDIT_EFFECT: {
                final String mediaItemId =
                    extras.getStringExtra(EffectsActivity.PARAM_MEDIA_ITEM_ID);
                final int type = extras.getIntExtra(EffectsActivity.PARAM_EFFECT_TYPE,
                        EffectType.EFFECT_COLOR_GRADIENT);
                if (mProject != null) {
                    mMediaLayout.editEffect(type, mediaItemId);
                } else {
                    // Add this overlay after you load the project
                    mEditEffectMediaItemId = mediaItemId;
                    mEditEffectType = type;
                }
                break;
            }

            case REQUEST_CODE_KEN_BURNS: {
                final String mediaItemId =
                    extras.getStringExtra(KenBurnsActivity.PARAM_MEDIA_ITEM_ID);
                final Rect startRect =
                    extras.getParcelableExtra(KenBurnsActivity.PARAM_START_RECT);
                final Rect endRect =
                    extras.getParcelableExtra(KenBurnsActivity.PARAM_END_RECT);
                if (mProject != null) {
                    mMediaLayout.addKenBurnsEffect(mediaItemId, startRect, endRect);
                } else {
                    mSetKenBurnsMediaItemId = mediaItemId;
                    mSetKenBurnsStartRect = startRect;
                    mSetKenBurnsEndRect = endRect;
                }
                break;
            }

            default: {
                break;
            }
        }
    }

    /*
     * {@inheritDoc}
     */
    public void surfaceCreated(SurfaceHolder holder) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "surfaceCreated");
        }

        mPreviewThread = new PreviewThread(mSurfaceHolder);
    }

    /*
     * {@inheritDoc}
     */
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "surfaceChanged: " + width + "x" + height);
        }
    }

    /*
     * {@inheritDoc}
     */
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "surfaceDestroyed");
        }

        // Stop the preview playback if pending and quit the preview thread
        if (mPreviewThread != null) {
            mPreviewThread.stopPreviewPlayback();
            mPreviewThread.quit();
            mPreviewThread = null;
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void enterTransitionalState(int statusStringId) {
        mEditorProjectView.setVisibility(View.GONE);
        mEditorEmptyView.setVisibility(View.VISIBLE);

        ((TextView)findViewById(R.id.empty_project_text)).setText(statusStringId);
        findViewById(R.id.empty_project_progress).setVisibility(View.VISIBLE);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void enterDisabledState(int statusStringId) {
        mEditorProjectView.setVisibility(View.GONE);
        mEditorEmptyView.setVisibility(View.VISIBLE);

        getActionBar().setTitle(R.string.app_name);

        ((TextView)findViewById(R.id.empty_project_text)).setText(statusStringId);
        findViewById(R.id.empty_project_progress).setVisibility(View.GONE);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void enterReadyState() {
        mEditorProjectView.setVisibility(View.VISIBLE);
        mEditorEmptyView.setVisibility(View.GONE);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected boolean showPreviewFrame() {
        if (mPreviewThread == null) { // The surface is not ready
            return false;
        }

        // Regenerate the preview frame
        if (mProject != null && !mPreviewThread.isPlaying() && mPendingExportFilename == null) {
            // Display the preview frame
            mPreviewThread.previewFrame(mProject, mProject.getPlayheadPos(),
                    mProject.getMediaItemCount() == 0);
        }

        return true;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void updateTimelineDuration() {
        if (mProject == null) {
            return;
        }

        final long durationMs = mProject.computeDuration();

        // Resize the timeline according to the new timeline duration
        final int zoomWidth = mActivityWidth + timeToDimension(durationMs);
        final int childrenCount = mTimelineLayout.getChildCount();
        for (int i = 0; i < childrenCount; i++) {
            final View child = mTimelineLayout.getChildAt(i);
            final ViewGroup.LayoutParams lp = child.getLayoutParams();
            lp.width = zoomWidth;
            child.setLayoutParams(lp);
        }

        mTimelineLayout.requestLayout(mLayoutCallback);

        // Since the duration has gone down make sure that the playhead
        // position is correct.
        if (mProject.getPlayheadPos() > durationMs) {
            movePlayhead(durationMs);
        }

        mAudioTrackLayout.updateTimelineDuration();
    }

    /**
     * Convert the time to dimension
     * At zoom level 1: one activity width = 1200 seconds
     * At zoom level 2: one activity width = 600 seconds
     * ...
     * At zoom level 100: one activity width = 12 seconds
     *
     * At zoom level 1000: one activity width = 1.2 seconds
     *
     * @param durationMs The time
     *
     * @return The dimension
     */
    private int timeToDimension(long durationMs) {
        return (int)((mProject.getZoomLevel() * mActivityWidth * durationMs) / 1200000);
    }

    /**
     * Zoom the timeline
     *
     * @param level The zoom level
     * @param setBar true to set the SeekBar position to match the zoom level
     */
    private int zoomTimeline(int level, boolean setBar) {
        if (level < 1 || level > MAX_ZOOM_LEVEL) {
            return mProject.getZoomLevel();
        }

        mProject.setZoomLevel(level);
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "zoomTimeline level: " + level + " -> " + timeToDimension(1000) + " pix/s");
        }

        updateTimelineDuration();

        if (setBar) {
            mZoomBar.setProgress(level);
        }
        return level;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void movePlayhead(long timeMs) {
        if (mProject == null) {
            return;
        }

        if (setPlayhead(timeMs)) {
            // Scroll the timeline such that the specified position
            // is in the center of the screen
            mTimelineScroller.appScrollTo(timeToDimension(timeMs), true);
        }
    }

    /**
     * Set the playhead at the specified time position
     *
     * @param timeMs The time position
     *
     * @return true if the playhead was set at the specified time position
     */
    private boolean setPlayhead(long timeMs) {
        // Check if the position would change
        if (mCurrentPlayheadPosMs == timeMs) {
            return false;
        }

        // Check if the time is valid. Note that invalid values are common due
        // to overscrolling the timeline
        if (timeMs < 0) {
            return false;
        } else if (timeMs > mProject.computeDuration()) {
            return false;
        }

        mCurrentPlayheadPosMs = timeMs;

        mTimeView.setText(StringUtils.getTimestampAsString(this, timeMs));
        mProject.setPlayheadPos(timeMs);
        return true;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void setAspectRatio(final int aspectRatio) {
        final FrameLayout.LayoutParams lp =
            (FrameLayout.LayoutParams)mSurfaceView.getLayoutParams();

        switch (aspectRatio) {
            case MediaProperties.ASPECT_RATIO_5_3: {
                lp.width = (lp.height * 5) / 3;
                break;
            }

            case MediaProperties.ASPECT_RATIO_4_3: {
                lp.width = (lp.height * 4) / 3;
                break;
            }

            case MediaProperties.ASPECT_RATIO_3_2: {
                lp.width = (lp.height * 3) / 2;
                break;
            }

            case MediaProperties.ASPECT_RATIO_11_9: {
                lp.width = (lp.height * 11) / 9;
                break;
            }

            case MediaProperties.ASPECT_RATIO_16_9: {
                lp.width = (lp.height * 16) / 9;
                break;
            }

            default: {
                break;
            }
        }

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "setAspectRatio: " + aspectRatio + ", size: " + lp.width + "x" + lp.height);
        }
        mSurfaceView.setLayoutParams(lp);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected MediaLinearLayout getMediaLayout() {
        return mMediaLayout;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected OverlayLinearLayout getOverlayLayout() {
        return mOverlayLayout;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected AudioTrackLinearLayout getAudioTrackLayout() {
        return mAudioTrackLayout;
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onExportProgress(int progress) {
        if (mExportProgressDialog != null) {
            mExportProgressDialog.setProgress(progress);
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onExportComplete() {
        if (mExportProgressDialog != null) {
            mExportProgressDialog.dismiss();
            mExportProgressDialog = null;
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void onProjectEditStateChange(boolean projectEdited) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "onProjectEditStateChange: " + projectEdited);
        }
        mPreviewPlayButton.setAlpha(projectEdited ? 100 : 255);
        mPreviewPlayButton.setEnabled(!projectEdited);
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected void initializeFromProject(boolean updateUI) {
        if (updateUI) {
            getActionBar().setTitle(mProject.getName());

            // Clear the media related to the previous project and
            // add the media for the current project.
            mMediaLayout.setProject(mProject);
            mOverlayLayout.setProject(mProject);
            mAudioTrackLayout.setProject(mProject);
            mPlayheadView.setProject(mProject);

            // Add the media items to the media item layout
            mMediaLayout.addMediaItems(mProject.getMediaItems());

            // Add the media items to the overlay layout
            mOverlayLayout.addMediaItems(mProject.getMediaItems());

            // Add the audio tracks to the audio tracks layout
            mAudioTrackLayout.addAudioTracks(mProject.getAudioTracks());

            setAspectRatio(mProject.getAspectRatio());
        }

        updateTimelineDuration();
        zoomTimeline(mProject.getZoomLevel(), true);

        // Set the playhead position. We need to wait for the layout to
        // complete before we can scroll to the playhead position.
        final Handler handler = new Handler();
        handler.post(new Runnable() {
            private final long DELAY = 100;
            private final int ATTEMPTS = 20;
            private int mAttempts = ATTEMPTS;

            /*
             * {@inheritDoc}
             */
            public void run() {
                if (mAttempts == ATTEMPTS) { // Only scroll once
                    movePlayhead(mProject.getPlayheadPos());
                }

                // If the surface is not yet created (showPreviewFrame()
                // returns false) wait for a while (DELAY * ATTEMPTS).
                if (showPreviewFrame() == false && mAttempts >= 0) {
                    mAttempts--;
                    if (mAttempts >= 0) {
                        handler.postDelayed(this, DELAY);
                    }
                }
            }
        });

        if (mAddMediaItemVideoUri != null) {
            ApiService.addMediaItemVideoUri(this, mProjectPath, ApiService.generateId(),
                    mInsertMediaItemAfterMediaItemId,
                    mAddMediaItemVideoUri, MediaItem.RENDERING_MODE_BLACK_BORDER,
                    mProject.getTheme());
            mAddMediaItemVideoUri = null;
            mInsertMediaItemAfterMediaItemId = null;
        }

        if (mAddMediaItemImageUri != null) {
            ApiService.addMediaItemImageUri(this, mProjectPath, ApiService.generateId(),
                    mInsertMediaItemAfterMediaItemId,
                    mAddMediaItemVideoUri, MediaItem.RENDERING_MODE_BLACK_BORDER,
                    MediaItemUtils.getDefaultImageDuration(), mProject.getTheme());
            mAddMediaItemImageUri = null;
            mInsertMediaItemAfterMediaItemId = null;
        }

        if (mAddAudioTrackUri != null) {
            ApiService.addAudioTrack(this, mProject.getPath(), ApiService.generateId(),
                    mAddAudioTrackUri, true);
            mAddAudioTrackUri = null;
        }

        if (mAddTransitionAfterMediaId != null) {
            mMediaLayout.addTransition(mAddTransitionAfterMediaId, mAddTransitionType,
                    mAddTransitionDurationMs);
            mAddTransitionAfterMediaId = null;
        }

        if (mEditTransitionId != null) {
            mMediaLayout.editTransition(mEditTransitionAfterMediaId, mEditTransitionId,
                    mEditTransitionType, mEditTransitionDurationMs);
            mEditTransitionId = null;
            mEditTransitionAfterMediaId = null;
        }

        if (mAddOverlayMediaItemId != null) {
            ApiService.addOverlay(this, mProject.getPath(), mAddOverlayMediaItemId,
                    ApiService.generateId(), mAddOverlayUserAttributes, 0,
                    OverlayLinearLayout.DEFAULT_TITLE_DURATION);
            mAddOverlayMediaItemId = null;
            mAddOverlayUserAttributes = null;
        }

        if (mEditOverlayMediaItemId != null) {
            ApiService.setOverlayUserAttributes(this, mProject.getPath(), mEditOverlayMediaItemId,
                    mEditOverlayId, mEditOverlayUserAttributes);

            mEditOverlayMediaItemId = null;
            mEditOverlayId = null;
            mEditOverlayUserAttributes = null;
        }

        if (mAddEffectMediaItemId != null) {
            mMediaLayout.addEffect(mAddEffectType, mAddEffectMediaItemId);
            mAddEffectMediaItemId = null;
        }

        if (mEditEffectMediaItemId != null) {
            mMediaLayout.editEffect(mEditEffectType, mEditEffectMediaItemId);
            mEditEffectMediaItemId = null;
        }

        if (mSetKenBurnsMediaItemId != null) {
            mMediaLayout.addKenBurnsEffect(mSetKenBurnsMediaItemId, mSetKenBurnsStartRect,
                    mSetKenBurnsEndRect);
            mSetKenBurnsMediaItemId = null;
        }

        enterReadyState();

        if (mPendingExportFilename != null) {
            if (ApiService.isVideoEditorExportPending(mProjectPath, mPendingExportFilename)) {
                // The export is still pending
                // Display the export project dialog
                showExportProgress();
            } else {
                // The export completed while the Activity was paused
                mPendingExportFilename = null;
            }
        }

        invalidateOptionsMenu();
    }

    /**
     * Show progress during export operation
     */
    private void showExportProgress() {
        mExportProgressDialog = new ProgressDialog(this);
        mExportProgressDialog.setTitle(getString(R.string.export_dialog_export));
        mExportProgressDialog.setMessage(null);
        mExportProgressDialog.setIndeterminate(false);
        mExportProgressDialog.setCancelable(true);
        mExportProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mExportProgressDialog.setMax(100);
        mExportProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            /*
             * {@inheritDoc}
             */
            public void onCancel(DialogInterface dialog) {
                ApiService.cancelExportVideoEditor(VideoEditorActivity.this,
                        mProjectPath, mPendingExportFilename);
                mPendingExportFilename = null;
                mExportProgressDialog = null;
            }
        });

        mExportProgressDialog.show();
    }

    /**
     * The preview thread
     */
    private class PreviewThread extends Thread {
        private final Handler mHandler;
        private final Queue<Runnable> mQueue;
        private final SurfaceHolder mSurfaceHolder;
        private Handler mThreadHandler;
        private VideoEditorProject mPlayingProject;

        private final Runnable mProcessQueueRunnable = new Runnable() {
            /*
             * {@inheritDoc}
             */
            public void run() {
                // Process whatever accumulated in the queue
                Runnable runnable;
                while ((runnable = mQueue.poll()) != null) {
                    runnable.run();
                }
            }
        };

        /**
         * Constructor
         *
         * @param surfaceHolder The surface holder
         */
        public PreviewThread(SurfaceHolder surfaceHolder) {
            mHandler = new Handler(Looper.getMainLooper());
            mQueue = new LinkedBlockingQueue<Runnable>();
            mSurfaceHolder = surfaceHolder;
            mPlayingProject = null;
            start();
        }

        /**
         * Preview the specified frame
         *
         * @param project The video editor project
         * @param timeMs The frame time
         * @param clear true to clear the output
         */
        public void previewFrame(final VideoEditorProject project, final long timeMs,
                final boolean clear) {
            if (mPlayingProject != null) {
                stopPreviewPlayback();
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Preview frame at: " + timeMs + " " + clear);
            }

            // We only need to see the last frame
            mQueue.clear();

            mQueue.add(new Runnable() {
                /*
                 * {@inheritDoc}
                 */
                public void run() {
                    if (clear) {
                        clearSurface();
                    } else {
                        try {
                            if (project.renderPreviewFrame(mSurfaceHolder, timeMs) < 0) {
                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "Clear frame at: " + timeMs +
                                            " of " + mProject.computeDuration());
                                }
                                clearSurface();
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Requested timeMs: " + timeMs);
                            ex.printStackTrace();
                        }
                    }
                }
            });

            if (mThreadHandler != null) {
                mThreadHandler.post(mProcessQueueRunnable);
            }
        }

        /**
         * Display the frame at the specified time position
         *
         * @param mediaItem The media item
         * @param timeMs The frame time
         */
        public void renderMediaItemFrame(final MovieMediaItem mediaItem, final long timeMs) {
            if (mPlayingProject != null) {
                stopPreviewPlayback();
            }

            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "Render media item frame at: " + timeMs);
            }

            // We only need to see the last frame
            mQueue.clear();

            mQueue.add(new Runnable() {
                /*
                 * {@inheritDoc}
                 */
                public void run() {
                    try {
                        if (mProject.renderMediaItemFrame(mSurfaceHolder, mediaItem.getId(),
                                timeMs) < 0) {
                            clearSurface();
                        }
                    } catch (Exception ex) {
                        // For now handle the NullPointerException in the NXP code
                        ex.printStackTrace();
                    }
                }
            });

            if (mThreadHandler != null) {
                mThreadHandler.post(mProcessQueueRunnable);
            }
        }

        /**
         * Clear the surface (render a black frame)
         */
        private void clearSurface() {
            // TODO: Call the new API method to clear the frame
        }

        /**
         * Start the preview playback
         *
         * @param project The video editor project
         * @param fromMs Start playing from the specified position
         */
        private void startPreviewPlayback(final VideoEditorProject project, final long fromMs) {
            if (mPlayingProject != null) {
                if (mPlayingProject.getPath().equals(project.getPath())) {
                    // This project is already playing
                    return;
                } else {
                    // Another project is playing. Stop that playback.
                    stopPreviewPlayback();
                }
            }

            previewStarted(project);
            // Clear any pending preview frames
            mQueue.clear();
            mQueue.add(new Runnable() {
                /*
                 * {@inheritDoc}
                 */
                public void run() {
                    try {
                        project.startPreview(mSurfaceHolder, fromMs, -1, false, 3,
                                new VideoEditor.PreviewProgressListener() {
                            /*
                             * {@inheritDoc}
                             */
                            public void onProgress(VideoEditor videoEditor, final long timeMs,
                                    final boolean end) {
                                mHandler.post(new Runnable() {
                                    /*
                                     * {@inheritDoc}
                                     */
                                    public void run() {
                                        if (mPlayingProject != null) {
                                            if (end) {
                                                previewStopped();
                                            } else {
                                                movePlayhead(timeMs);
                                            }
                                        }
                                    }
                                });
                            }
                        });
                    } catch (Exception ex) {
                        // This exception may occur when trying to play frames
                        // at the end of the timeline
                        // (e.g. when fromMs == clip duration)
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Cannot start preview at: " + fromMs, ex);
                        } else {
                            Log.d(TAG, "Cannot start preview at: " + fromMs);
                        }
                    }
                }
            });

            if (mThreadHandler != null) {
                mThreadHandler.post(mProcessQueueRunnable);
            }
        }

        /**
         * Stop previewing
         */
        private void stopPreviewPlayback() {
            if (mPlayingProject == null) {
                return;
            }

            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Stop preview: " + mPlayingProject.getPath());
            }

            previewStopped();
        }

        /**
         * The preview started.
         * This method is always invoked from the UI thread.
         *
         * @param project The project
         */
        private void previewStarted(VideoEditorProject project) {
            // Change the button image back to a play icon
            mPreviewPlayButton.setImageResource(R.drawable.btn_playback_ic_pause);

            mTimelineScroller.enableUserScrolling(false);
            mMediaLayout.setPlaybackInProgress(true);
            mOverlayLayout.setPlaybackInProgress(true);
            mAudioTrackLayout.setPlaybackInProgress(true);

            mPlayingProject = project;
        }

        /**
         * Preview stopped.
         * This method is always invoked from the UI thread.
         */
        private void previewStopped() {
            // Change the button image back to a play icon
            mPreviewPlayButton.setImageResource(R.drawable.btn_playback_ic_play);

            // Set the playhead position at the position where the playback stopped
            final long stopTimeMs = mPlayingProject.stopPreview();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Stopped at: " + stopTimeMs);
            }
            movePlayhead(stopTimeMs);

            mPlayingProject = null;

            // The playback has stopped
            mTimelineScroller.enableUserScrolling(true);
            mMediaLayout.setPlaybackInProgress(false);
            mAudioTrackLayout.setPlaybackInProgress(false);
            mOverlayLayout.setPlaybackInProgress(false);
        }

        /**
         * @return true if preview playback is in progress
         */
        private boolean isPlaying() {
            return (mPlayingProject != null);
        }

        /*
         * {@inheritDoc}
         */
        @Override
        public void run() {
            setPriority(MAX_PRIORITY);
            Looper.prepare();
            mThreadHandler = new Handler();

            // Ensure that the queued items are processed
            mHandler.post(new Runnable() {
                /*
                 * {@inheritDoc}
                 */
                public void run() {
                    mThreadHandler.post(mProcessQueueRunnable);
                }
            });

            // Run the loop
            Looper.loop();
        }

        /**
         * Quit the thread
         */
        public void quit() {
            if (mThreadHandler != null) {
                mThreadHandler.getLooper().quit();
                try {
                    // Wait for the thread to quit. An ANR waiting to happen.
                    mThreadHandler.getLooper().getThread().join();
                } catch (InterruptedException ex) {
                }
            }

            mQueue.clear();
        }
    }
}
