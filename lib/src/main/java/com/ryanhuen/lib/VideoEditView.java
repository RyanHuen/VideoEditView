package com.ryanhuen.lib;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.widget.ViewDragHelper;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Created by ryanhuen on 18-5-16.
 */

public class VideoEditView extends FrameLayout {

    public static final String TAG = VideoEditView.class.getName();
    private PlayThread mPlayThread;

    public interface OnViewDragListener {
        void onDragDone(float percentOfPosition);
    }

    public interface OnPlayProgressListener {
        /**
         * @return 用于给VideoView设置播放进度，将Cursor跳转到对应位置
         */
        long getCurrentPosition();
    }

    private OnViewDragListener mDragListener;
    private OnPlayProgressListener mProgressListener;
    private ViewDragHelper mViewDragHelper;
    private View mDragCursorView;
    private int mCursorViewWidth;
    private long mVideoTotalDuration;
    private long mCurrentVideoPosition;

    private final Object mCurrDurationLock = new Object();

    public VideoEditView(@NonNull Context context) {
        super(context);
        init();
    }

    public VideoEditView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VideoEditView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mViewDragHelper = ViewDragHelper.create(this, 1.0f, new ViewDragCallback());
    }

    /**
     * 用于调用者传入时间Cursor的宽度
     *
     * @param cursorViewWidth 时间Cursor宽度
     */
    public void setCursorViewWidth(int cursorViewWidth) {
        mCursorViewWidth = cursorViewWidth;
    }

    /**
     * 传入可拖动的DragCursorView
     * 默认会对传入的View进行Unspecified的measure，取measure后大小。
     * <p>
     * 如果有特殊宽度要求，请调用 {@see #setCursorViewWidth}  传入大小
     *
     * @param dragCursorView 可拖动的CursorView
     */
    public void setDragCursorView(View dragCursorView) {
        mDragCursorView = dragCursorView;
        mDragCursorView.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        mCursorViewWidth = mDragCursorView.getMeasuredWidth();
    }

    public void setVideoDuration(long duration) {
        mVideoTotalDuration = duration;
    }

    public void setDragListener(OnViewDragListener dragListener) {
        mDragListener = dragListener;
    }

    public void setProgressListener(OnPlayProgressListener progressListener) {
        mProgressListener = progressListener;
    }

    public long getCurrentVideoPosition() {
        return mCurrentVideoPosition;
    }

    private void seekCursorPosition() {
        Log.d("ryanhuen", "VideoEditView"+" : seekCursorPosition: ");
        float rate = mCurrentVideoPosition * 1.0f / mVideoTotalDuration;
        int position = (int) (getWidth() * rate) - (mCursorViewWidth / 2);
        mDragCursorView.setLeft(position);
        mDragCursorView.setRight(position + mCursorViewWidth);
        mDragCursorView.invalidate();
    }

    public void start() {
        if (mPlayThread == null) {
            mPlayThread = new PlayThread();
            mPlayThread.startPlaying();
        } else {
            restart();
        }
    }

    public void resume() {
        if (mPlayThread != null) {
            mPlayThread.resumePlaying();
        }
    }

    public boolean isPausing() {
        if (mPlayThread != null) {
            return mPlayThread.isPausing();
        }
        return false;
    }

    public void pause() {
        if (mPlayThread != null) {
            mPlayThread.pause();
        }
    }


    public void stop() {
        if (mPlayThread != null) {
            mPlayThread.stopPlaying();
            mPlayThread = null;
        }
    }

    public void restart() {
        stop();
        start();
    }

    class PlayThread extends Thread {
        public static final byte STATE_PLAYING = 1;
        public static final byte STATE_PAUSING = 2;
        public static final byte STATE_STOPPING = 3;

        private long mLastDuration = -1;
        private byte mState = STATE_STOPPING;
        private final Object mStateLock = new Object();

        @Override
        public void run() {
            super.run();
            mState = STATE_PLAYING;
            mLastDuration = -1;
            while (mCurrentVideoPosition <= mVideoTotalDuration) {
                synchronized (mStateLock) {
                    if (mState == STATE_PAUSING) {
                        try {
                            Log.d("ryanhuen", "PlayThread" + " : run: +wait");
                            mStateLock.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else if (mState == STATE_STOPPING) {
                        mCurrentVideoPosition = 0;
                        break;
                    }
                }
                synchronized (mCurrDurationLock) {
                    if (mProgressListener != null) {
                        mCurrentVideoPosition = mProgressListener.getCurrentPosition();
                    }
                }
                if (mCurrentVideoPosition != mLastDuration) {
                    seekCursorPosition();
                    mLastDuration = mCurrentVideoPosition;
                }
                try {
                    sleep(100);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public void startPlaying() {
            this.start();
        }

        public void resumePlaying() {
            synchronized (mStateLock) {
                mState = STATE_PLAYING;
                mStateLock.notify();
            }
        }

        public void pause() {
            synchronized (mStateLock) {
                mState = STATE_PAUSING;
            }
        }

        public void stopPlaying() {
            synchronized (mStateLock) {
                mState = STATE_STOPPING;
                mStateLock.notify();
            }
            try {
                this.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            mCurrentVideoPosition = 0;
        }

        public boolean isPausing() {
            return mState == STATE_PAUSING;
        }
    }

    private class ViewDragCallback extends ViewDragHelper.Callback {

        @Override
        public boolean tryCaptureView(View child, int pointerId) {
            if (child == mDragCursorView) {
                return true;
            }
            return false;
        }

        /**
         * 当拖拽到状态改变时回调
         *
         * @params 新的状态
         */
        @Override
        public void onViewDragStateChanged(int state) {
            switch (state) {
                case ViewDragHelper.STATE_DRAGGING:  // 正在被拖动
                    if (!isPausing()) {
                        pause();
                    }
                    break;
                case ViewDragHelper.STATE_IDLE:  // view没有被拖拽或者 正在进行fling/snap
                    if (mDragListener != null) {
                        mDragListener.onDragDone(mCurrentLeft * 1.0f / getWidth());
                    }
                    break;
                case ViewDragHelper.STATE_SETTLING: // fling完毕后被放置到一个位置
                    break;
            }
            super.onViewDragStateChanged(state);
        }

        int mCurrentLeft;

        @Override
        public void onViewPositionChanged(View changedView, int left, int top, int dx, int dy) {
            super.onViewPositionChanged(changedView, left, top, dx, dy);
            mCurrentLeft = left + mCursorViewWidth / 2;
        }

        @Override
        public int getViewHorizontalDragRange(View child) {
            return super.getViewHorizontalDragRange(child);
        }

        @Override
        public int getViewVerticalDragRange(View child) {
            return super.getViewVerticalDragRange(child);
        }


        /**
         * 处理水平方向上的拖动
         *
         * @param child 被拖动到view
         * @param left  移动到达的x轴的距离
         * @param dx    建议的移动的x距离
         * @return
         */
        @Override
        public int clampViewPositionHorizontal(View child, int left, int dx) {

            if (left < 0 && mCursorViewWidth / 2 < Math.abs(left)) {
                int tmp = -Math.abs(mCursorViewWidth / 2);
                return tmp;
            }

            if (getWidth() - child.getWidth() / 2 < left) {
                return getWidth() - child.getWidth() / 2;
            }

            return left;
        }

        @Override
        public int clampViewPositionVertical(View child, int top, int dy) {
            return super.clampViewPositionVertical(child, top, dy);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_DOWN:
                mViewDragHelper.cancel(); // 相当于调用 processTouchEvent收到ACTION_CANCEL
                break;
        }

        return mViewDragHelper.shouldInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        mViewDragHelper.processTouchEvent(event);
        return true;
    }
}
