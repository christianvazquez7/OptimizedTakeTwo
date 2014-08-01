package com.example.taketwo.app;

import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.view.MotionEvent;


import com.google.android.glass.touchpad.GestureDetector;


public class MediaActivity extends FragmentActivity {
    private static final String TAG = "MediaActivity";
    public static final String MODE_KEY = "MODE";
    public static final String MODE_MEDIA = "MODE_MEDIA";
    private GestureDetector gestureDetector;
    private Fragment fragment;

    protected Fragment createFragment() {

            return new MediaPlayerFragment().newInstance((Uri) getIntent().getParcelableExtra(MediaPlayerFragment.ARG_URL), getIntent().getBooleanExtra(MediaPlayerFragment.ARG_LOOP, false));

    }

    protected int getLayoutResId() {
        return R.layout.activity_fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(getLayoutResId());
        FragmentManager manager = getSupportFragmentManager();
        fragment =  manager.findFragmentById(R.id.fragmentContainer);
        if (fragment == null) {
            fragment = createFragment();
            manager.beginTransaction()
                    .add(R.id.fragmentContainer, fragment)
                    .commit();
        }
        gestureDetector = new GestureDetector(this);
        MediaPlayerFragment fragment1 = (MediaPlayerFragment) fragment;
        gestureDetector.setBaseListener(fragment1);
        gestureDetector.setScrollListener(fragment1);
    }



    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }



    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if (gestureDetector != null) {
            return gestureDetector.onMotionEvent(event);
        }
        return false;
    }




}
