package com.ryanhuen.videoeditview;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.ImageView;
import android.widget.Toast;

import com.ryanhuen.lib.VideoEditView;

public class MainActivity extends AppCompatActivity implements VideoEditView.OnPlayProgressListener {

    private VideoEditView mVideoEditView;
    private ImageView mEditCursor;
    private int mCurrentProgress;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mVideoEditView = findViewById(R.id.video_edit_view);
        mEditCursor = findViewById(R.id.video_edit_cursor);

        mVideoEditView.setDragCursorView(mEditCursor);
        mVideoEditView.setDragListener(new VideoEditView.OnViewDragListener() {
            @Override
            public void onDragDone(float percentOfPosition) {
                Toast.makeText(MainActivity.this,
                        "当前进度百分比：" + (percentOfPosition * 100) + "%", Toast.LENGTH_SHORT).show();
            }
        });

        initVideoProgress();
    }

    private void initVideoProgress() {
        mVideoEditView.setVideoDuration(100);
        mVideoEditView.setProgressListener(this);
        mVideoEditView.start();
        new Thread() {
            @Override
            public void run() {
                super.run();
                for (int i = 0; i <= 100; i++) {
                    try {
                        Thread.sleep(100);
                        mCurrentProgress = i;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    @Override
    public long getCurrentPosition() {
        return mCurrentProgress;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mVideoEditView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mVideoEditView.pause();
    }
}
