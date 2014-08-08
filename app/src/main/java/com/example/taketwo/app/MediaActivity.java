package com.example.taketwo.app;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import com.google.android.glass.touchpad.GestureDetector;

public class MediaActivity extends FragmentActivity implements SurfaceHolder.Callback {
    private static final String TAG = "MediaActivity";
    public static final String MODE_KEY = "MODE";
    public static final String MODE_MEDIA = "MODE_MEDIA";
    private GestureDetector gestureDetector;
    private GestureFragment fragment;
    private ServiceConnection mConnection;
    private MediaRecordingService rs;
    public static SurfaceView mSurfaceView;

    protected GestureFragment createFragment() {
            return new MediaPlayerFragment().newInstance((Uri) getIntent().getParcelableExtra(MediaPlayerFragment.ARG_URL), getIntent().getBooleanExtra(MediaPlayerFragment.ARG_LOOP, false));

    }

    protected int getLayoutResId() {
        return R.layout.activity_fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());
        FrameLayout frame = (FrameLayout) this.findViewById(R.id.dummy);
        mSurfaceView = new SurfaceView(this);
        frame.addView(mSurfaceView);
        mSurfaceView.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
        mSurfaceView.getHolder().addCallback(this);
        FragmentManager manager = getSupportFragmentManager();
        fragment = (GestureFragment) manager.findFragmentById(R.id.fragmentContainer);
        if (fragment == null) {
            fragment = createFragment();
            manager.beginTransaction()
                    .add(R.id.fragmentContainer, fragment)
                    .commit();
        }
        gestureDetector = new GestureDetector(this);
        gestureDetector.setBaseListener(fragment);
        gestureDetector.setScrollListener(fragment);
        mConnection = new ServiceConnection() {
            public void onServiceConnected(ComponentName className, IBinder service) {
                rs = ((MediaRecordingService.MediaBinder) service).getService();
                rs.setSurfaceView(mSurfaceView);
                MediaPlayerFragment mediaFragment = (MediaPlayerFragment) fragment;
                mediaFragment.setServiceHandle(rs);
                mediaFragment.start(null);
            }

            public void onServiceDisconnected(ComponentName className) {
                Log.i(TAG, "Service Disconnected");
            }
        };
    }

    @Override
    public void onPause() {
        if (rs != null) {
            rs.stopRecording();
            rs.releaseRecorder();
        }
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if (mConnection != null) {
            unbindService(mConnection);
            mConnection = null;
        }
        super.onDestroy();
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (gestureDetector != null) {
            return gestureDetector.onMotionEvent(event);
        }
        return false;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.d("CREATED", "Surface made");
        MediaActivity.this.startService(new Intent(MediaActivity.this,
                MediaRecordingService.class));
        MediaActivity.this.bindService(new Intent(MediaActivity.this,
                MediaRecordingService.class), mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i2, int i3) {}

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {}


}
