package com.example.taketwo.app;

import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import com.google.android.glass.touchpad.Gesture;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;



public class MediaPlayerFragment extends GestureFragment implements MediaPlayer.OnErrorListener, MediaPlayer.OnPreparedListener , MediaPlayer.OnCompletionListener {
    public static final String ARG_URL = "ARG_URL";
    public static final String ARG_LOOP = "ARG_LOOP";
    private static final String TAG = "MediaPlayerFragment";
    private  MediaPlayer mp;

    private Uri mediaUri;
    private SurfaceHolder holder;
    private ProgressBar progressBar;
    private SurfaceView surfaceView;
    private Handler stutterHandler;
    private Runnable stutterThread;
    private boolean interrupt;
    private List<Integer> seekTimes;
    private long prevJumpTime;
    private int seekPosition = 0;
    private RelativeLayout relative;
    private MediaRecordingService rs;
    private ArrayList<String> fileFragments = new ArrayList<String>();
    private MediaHUD hud;
    private MediaController controller;
    private Handler handler = new Handler();
    private SeekBar seekBar;
    private long currentTime;
    private long recording;
    private CompositeFile videos;
    private FileEntry currentFile = null;
    private Handler seekBarHandler = new Handler();
    private Runnable updateSeekBar;
    private RelativeLayout barBackground;
    private boolean isWaitingTap = false;
    private Handler mergeHandler;
    private boolean inPresent = false;
    private boolean isMerging = false;
    private Object lock = new Object();
    private Timer timeoutTimer = new Timer();
    private static final int timeout = 5000;
    private boolean jumping = false;
    private Object fileLock = new Object();
    private long lastJump = 0;
    public static final long jumpLimit = 350;
    private boolean swipeMode = false;
    private long beforeSwipe = 0;
    private Object bookmarkLock = new Object();
    private ArrayList<Float> tempBookmarks = new ArrayList<Float>();
    private long START;
    boolean cut = false;
    boolean startingRecording = false;
    Thread a;


    public static MediaPlayerFragment newInstance(Uri uri, boolean looping) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_URL, uri);
        args.putBoolean(ARG_LOOP, looping);
        MediaPlayerFragment f = new MediaPlayerFragment();
        f.setArguments(args);
        return f;
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRetainInstance(true);
        mediaUri = getArguments().getParcelable(ARG_URL);
        createMediaPlayer();
        videos = new CompositeFile(true);
        mergeHandler = new Handler();


    }

    private void createMediaPlayer() {
        if (progressBar != null && mediaUri != null)
            progressBar.setVisibility(View.VISIBLE);
        mp = new MediaPlayer();
        if (mediaUri != null) {
            try {
                mp.setDataSource(getActivity(), mediaUri);
            } catch (IOException e) {
                e.printStackTrace();
            }
            mp.setOnErrorListener(this);
            mp.setOnPreparedListener(this);

            if (getArguments().getBoolean(ARG_LOOP))
                mp.setLooping(true);
            mp.prepareAsync();
        } else {
            if (progressBar != null)
                progressBar.setVisibility(View.INVISIBLE);
        }
        mp.setOnCompletionListener(this);
    }

    private  void setMediaSource(Uri uri, boolean looping) {
        seekBarHandler.removeCallbacks(updateSeekBar);
        long t = System.currentTimeMillis();
        inPresent = false;
        mediaUri = uri;
        if (mp == null) {
            return;
        }
        if (mp.isPlaying()) {

            mp.stop();
        Log.d("TIME","After stop: "+ (System.currentTimeMillis() -t));
        }
        try {
            mp.reset();
            Log.d("TIME","After reset: "+ (System.currentTimeMillis() -t));

            String tail = videos.getTail().getFilePath();
            if(tail.equals(mediaUri.toString())) {
                throw new IllegalStateException("Cannot play tail");
            }
            mp.setDataSource(getActivity(), mediaUri);
            Log.d("TIME","After set ds: "+ (System.currentTimeMillis() -t));

        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mp.prepare();
            Log.d("TIME","After prepare: "+ (System.currentTimeMillis() -t));
            hud.clear();
            hud.hidePresent();

            mp.start();
        } catch(IOException e){}
    }

    public void start(String e) {
        hud.showPresent();
        inPresent = true;
        hud.showRecording();
        if(rs == null) {
            Log.d("TAG","rs is null");
        }
        while (rs == null){}
        String path = rs.generateOutputFile();
        rs.startRecord();
        seekBarHandler.post(updateSeekBar);
        videos.addFile(path, -1);
        START = System.currentTimeMillis();
    }

    public void handleInput(String action, int mag, int e) {

        if (!isMerging) {
            Log.d(TAG, "in onEvent()");

            if (action.equals("play")) {
                interrupt = true;
                hud.clear();
                mp.start();

            } else if (action.equals("stop")) {
                interrupt = true;
                mp.stop();
                getActivity().finish();
            } else if (action.equals("pause")) {
                interrupt = true;
                hud.showPause();
                mp.pause();
            } else if (action.equals("playReverse")) {
                playReverseFromEnd(mag);
            } else if (action.equals("jump")) {
                if (!swipeMode) {
                    synchronized (lock) {
                        interrupt = true;
                        if (System.currentTimeMillis() - lastJump > jumpLimit) {
                            boolean jumpValid = jump(mag);
                            lastJump = System.currentTimeMillis();
                        }
                    }
                }
            } else if (action.equals("playFastForward")) {
                playFastForwardFromBeginning(mag);
            } else if (action.equals("rewind")) {
                rewind(mag);
            } else if (action.equals("fastForward")) {
                fastForward(mag);
            } else if (action.equals("takeTwoRewind")) {
                takeTwoRewind(mag);
            } else if (action.equals("seekTo")) {
                mp.seekTo(mag);
            } else if (action.equals("seekBackwards")) {
                seekBackwards(mag);
            } else if (action.equals("jumpToPresent")) {
                if (!swipeMode) {
                    synchronized (lock) {
                        interrupt = true;
                        this.jumpToPresent();
                    }
                }
            } else if (action.equals("toggle")) {
                interrupt = true;
                this.toggle();
                if (swipeMode){
                    deploy();
                }
                swipeMode = false;
            } else if (action.equals("swipeMode")) {
                synchronized (lock) {
                    interrupt = true;
                    this.swipeMode();
                }
            } else if(action.equals("placeBookmark")) {
                synchronized (fileLock) {
                    Log.d("BOOKMARK","placing bookmark");
                    long now = System.currentTimeMillis();
                    long start = rs.getCurrentRecordingStartTimeMillis();
                    Log.d("BOOKMARKJ",""+videos.getDuration() + (now - start));
                    videos.placeBookmark(videos.getDuration() + (now - start));
                }
            } else if (action.equals("prevBookmark")){
                synchronized (lock) {
                    hud.showSkipBack();
                    this.jumpToBookmark(false);
                }
            } else if (action.equals("nextBookmark")) {
                synchronized (lock) {
                    if(!inPresent) {
                        hud.showSkipForward(true);
                        this.jumpToBookmark(true);
                    } else {
                        hud.showSkipForward(false);
                    }
                }
            }
        }
    }

    private void jumpToBookmark(boolean next) {
        Log.d("BOOKMARKJ","jumping to previous");

        FileTimeTuple jumpHere = null;
        if (currentFile == null) {
            long start = rs.getCurrentRecordingStartTimeMillis();
            long now = System.currentTimeMillis();
            if (next){
                jumpHere = videos.getNextBookmarkFromTuple(new FileTimeTuple(videos.getTail().getFilePath(), videos.getDuration() + (now - start)));
            } else {
                jumpHere = videos.getPrevBookmarkFromTuple(new FileTimeTuple(videos.getTail().getFilePath(), videos.getDuration() + (now - start)));
            }
        } else {
            if (next){
                jumpHere = videos.getNextBookmarkFromTuple(new FileTimeTuple
                        (currentFile.getFilePath(), mp.getCurrentPosition()));
            } else {
                jumpHere = videos.getPrevBookmarkFromTuple(new FileTimeTuple
                        (currentFile.getFilePath(), mp.getCurrentPosition()));
            }
        }

        if (jumpHere == null) {
            Log.d("BOOKMARKJ","getting null");
            return;
        }
        if (jumpHere.getFilePath().equals(videos.getTail().getFilePath())) {
            cutTail();
        }
        if (currentFile == null || !currentFile.getFilePath().equals(jumpHere.getFilePath())) {
            Uri newUri = Uri.fromFile(new File(jumpHere.getFilePath()));
            this.setMediaSource(newUri, false);
            currentFile = videos.getFileEntry(jumpHere.getFilePath());
            mp.seekTo((int) jumpHere.getTimeInFile());
            //seekBarHandler.post(updateSeekBar);
            Log.d("BOOKMARKJ","changing uri");

        } else {
            Log.d("BOOKMARKJ","seeking");
            mp.seekTo((int)jumpHere.getTimeInFile());
        }


    }
    private void deploy() {
        int position = seekBar.getProgress();
        Log.d("DEPLOY"," "+position);
        FileTimeTuple jumpHere =videos.getFileFromTime(position*1000);
        Log.d("DEPLOY"," "+ seekBar.getMax());

        if (jumpHere.getFilePath().equals(videos.getTail().getFilePath())) {

            cutTail();
            if (jumpHere.getTimeInFile() + videos.getFileEntry(jumpHere.getFilePath()).getStartTime() >= videos.getDuration() - 5000) {
                jumpHere.setTimeInFile(videos.getDuration() - 5000);
            }
        }

        if (currentFile == null || !currentFile.getFilePath().equals(jumpHere.getFilePath())) {
            Uri newUri = Uri.fromFile(new File(jumpHere.getFilePath()));
            this.setMediaSource(newUri, false);
            currentFile = videos.getFileEntry(jumpHere.getFilePath());
            mp.seekTo((int) jumpHere.getTimeInFile());
            //seekBarHandler.post(updateSeekBar);
        } else {
            mp.seekTo((int) jumpHere.getTimeInFile());
        }

    }
    private void swipeMode() {
        swipeMode = true;
        hud.showPause();
        mp.pause();
        hud.showSwipeMode();
        beforeSwipe = mp.getCurrentPosition();
    }

    @Override
    public boolean onScroll(float displacement, float delta, float velocity) {
        //Utils.eventBusPost(new MediaOnScrollEvent(displacement, delta, velocity));
        if (swipeMode) {
            Log.d("SWIPE_MODE","d: "+displacement/1300 +" delta: "+ delta+" v: "+velocity);
            float d = displacement/1300;
            int dif = 0;
            if (displacement > 0) {
                dif = seekBar.getMax() - seekBar.getProgress();
            } else {
                dif = seekBar.getProgress();
            }
            if(Math.abs(velocity) >= .1) {
                seekBar.setProgress((int) (seekBar.getProgress() + d * dif));
                FileTimeTuple jumpHere = videos.getFileFromTime((long)((seekBar.getProgress() + d* dif)* 1000));
                if (!jumpHere.getFilePath().equals(videos.getTail())){
                    if (currentFile!= null && jumpHere.getFilePath().equals(currentFile.getFilePath())){
                        mp.seekTo((int)jumpHere.getTimeInFile());
                    } else {
                        if(currentFile != null) {
                            Uri newUri = Uri.fromFile(new File(jumpHere.getFilePath()));
                            this.setMediaSource(newUri, false);
                            mp.pause();
                            currentFile = videos.getFileEntry(jumpHere.getFilePath());
                            mp.seekTo((int) jumpHere.getTimeInFile());

                            //seekBarHandler.post(updateSeekBar);
                        }
                    }
                } else {

                }

            }

        }
        return false;
    }

    private void toggle() {
        if (mp!= null) {

            boolean isPlaying = mp.isPlaying();

            if (isPlaying) {
                mp.pause();
                hud.showPause();
                timeoutTimer.cancel();
                timeoutTimer.purge();
                timeoutTimer = new Timer();
                interrupt = false;
                timeoutTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        if (!interrupt) {
                            jumpToPresent();
                        }
                    }
                }, timeout);

            } else {
                if(!inPresent) {
                    mp.start();
                    hud.clear();
                }
            }
            isPlaying = !isPlaying;
        }
    }
    private synchronized boolean jump(int jumpVectorMSecs) {
        //positive jumpVector jumps forward / negative vector jumps backwards total milliseconds
        if (jumpVectorMSecs == 0) return false;

        if(jumpVectorMSecs < 0) {
            hud.showSkipBack();
        } else {
            hud.showSkipForward(true);
        }

        this.isWaitingTap = false;

        FileTimeTuple fileTimeToSeek;
        if (videos.isTailFinished()) {
            if (currentFile == null) {
                // at the tail!
                //TODO: implement this logic
                fileTimeToSeek = null;
            } else {
                // normal case
                fileTimeToSeek = videos.getFileFromJump(
                        jumpVectorMSecs,
                        mp.getCurrentPosition(),
                        mediaUri.getPath());
            }
        } else {
            if (currentFile == null) {
                if (jumpVectorMSecs > 0) {
                    //TODO: show icon saying this operation is not allowed
                    hud.showSkipForward(false);
                    return false;
                }
                long start = rs.getCurrentRecordingStartTimeMillis();
                long now = System.currentTimeMillis();
                if (now + jumpVectorMSecs < start) {
                    // jump to previous recorded file, let recording continue
                    if (videos.getLastRecordedFile() == null){
                        cutTail();  //test
                    }
                    fileTimeToSeek = videos.getFileFromJump(
                            videos.endOfFile(videos.getLastRecordedFile()),
                            jumpVectorMSecs + (now - start));
                } else {
                    cutTail();
                    // seek to desired location in new file
                    fileTimeToSeek = videos.getFileFromJump(
                            videos.endOfFile(videos.getLastRecordedFile()),
                            jumpVectorMSecs);
                }
            } else {
                fileTimeToSeek = videos.getFileFromJump(
                        jumpVectorMSecs,
                        mp.getCurrentPosition(),
                        currentFile.getFilePath());
                //mediaUri.getPath());
                long start = rs.getCurrentRecordingStartTimeMillis();
                long now = System.currentTimeMillis();
                Log.d(TAG,videos.getTail().getFilePath());
                Log.d(TAG,fileTimeToSeek.getFilePath());
                if (videos.getTail().getFilePath().equals(fileTimeToSeek.getFilePath())) {
                    Log.d(TAG,"Cutting tail");
                    if (fileTimeToSeek.getTimeInFile() > now - start - 6000) {
                        // trying to fast forward to < 5 seconds behind the present
                        // make user be at least 5 seconds behind
                        Log.d("WARNING","Trying to jump to the future");
                        hud.showSkipForward(false);
                        return false;
                    } else {
                        cutTail();
                        fileTimeToSeek = videos.getFileFromTime(videos.getTime(fileTimeToSeek)); //this is returning the tail
                        if (fileTimeToSeek.getFilePath().equals(videos.getTail().getFilePath())) {
                            fileTimeToSeek = new FileTimeTuple(videos.getLastRecordedFile().getFilePath(), 0);
                            Log.d("WARNING","danger zone");
                        }
                    }
                }
            }
        }

        seekToFileTime(fileTimeToSeek);
        return true;
    }

    private void jumpToPresent() {

        seekBarHandler.removeCallbacks(updateSeekBar);
        synchronized (fileLock) {
            this.currentFile = null;
        }
        seekBarHandler.post(updateSeekBar);
        mp.stop();
        seekBar.setProgress(seekBar.getMax());
        hud.showPresent();
        inPresent = true;

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.VISIBLE);
            }
        });
        isMerging = true;
        hud.displayMerging(true);
        videos.flattenSmallFiles();
        isMerging = false;
        hud.displayMerging(false);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private synchronized void cutTail() {
        if (a != null) {
            try{
                a.join();} catch (InterruptedException e){}
        }
        long t = System.currentTimeMillis();
        rs.stopRecording();
        Log.d("TIME","after stop recording:" + (System.currentTimeMillis()-t));

        seekBarHandler.removeCallbacks(updateSeekBar);
        videos.setTailDuration(getDuration(videos.getTail().getFilePath()));
        seekBarHandler.post(updateSeekBar);
        cut = true;
        videos.addFile(rs.generateOutputFile(), -1);
        Log.d("TIME","after setting current time:" + (System.currentTimeMillis()-t));

        a =rs.recordAsync();

        Log.d("TIME","cut time:" + (System.currentTimeMillis()-t));
    }

    public long getCurrentPosition() {
        return currentFile.getStartTime() + mp.getCurrentPosition();
    }

    private void seekToFileTime(FileTimeTuple fileTime) {
        String filePathToSeek = fileTime.getFilePath();
        Uri newUri = Uri.fromFile(new File(filePathToSeek));
        if (!newUri.equals(mediaUri)) {
            Log.d(TAG,"new URI: "+newUri.toString());
            if(mediaUri != null)
                Log.d(TAG,"old URI: "+mediaUri.toString());
            //synchronized (fileLock) { //resynch !!!!!!
                currentFile = videos.getFileEntry(filePathToSeek);
           // }
            seekBarHandler.removeCallbacks(updateSeekBar);
            setMediaSource(newUri, false);
            int timeToSeek = (int)fileTime.getTimeInFile();
            mp.seekTo(timeToSeek);
            seekBarHandler.post(updateSeekBar);
            if (timeToSeek > mp.getDuration()) {
                hud.showSkipForward(false);
            }
        }
        mp.seekTo((int)fileTime.getTimeInFile());

    }

    private void seekBackwards(int msecs) {
        mp.seekTo(mp.getDuration());
        jump(-msecs);
    }

    private void stutter(int period) {

        final int p = period;
        stutterHandler = new Handler();
        mp.seekTo(mp.getDuration());
        currentTime = mp.getDuration();
        stutterThread = new Runnable() {
            public void run() {

                if (!interrupt) {
                    currentTime = currentTime - p;
                    if (currentTime <= 0) {
                        mp.seekTo(0);
                        mp.start();
                    } else {

                        //mp.seekTo(currentTime);
                        mp.start();
                        stutterHandler.postDelayed(stutterThread, p);

                    }
                }
            }
        };
        stutterHandler.postDelayed(stutterThread, period);
    }




    private void modifiedSpeedPlayback(final int speed, boolean forward, boolean fromEndpoint) {
        if (speed <= 0) return;
        mp.pause();
        final int startDelay = 100;
        seekTimes = new ArrayList<Integer>();
        final int duration = mp.getDuration();

        if (!forward) {
            if (fromEndpoint) {
                for (int i = duration; i >= 0; i -= speed) {
                    seekTimes.add(i);
                }
            } else {
                for (int i = mp.getCurrentPosition(); i >= 0; i -= speed) {
                    seekTimes.add(i);
                }
            }
        } else {
            if (fromEndpoint) {
                for (int i = 0; i < duration; i += speed) {
                    seekTimes.add(i);
                }
            } else {
                for (int i = mp.getCurrentPosition(); i < duration; i += speed) {
                    seekTimes.add(i);
                }
            }
        }

        mp.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mediaPlayer) {
                long currentTime = System.currentTimeMillis();
                //if time between jumps is more than twice the speed of jumps, skip a frame
                if (currentTime > prevJumpTime + 2 * speed) {
                    seekPosition++;
                }
                if (seekPosition < seekTimes.size() && !interrupt) {
                    seekPosition++;
                    prevJumpTime = currentTime;
                    mp.seekTo(seekTimes.get(seekPosition - 1));
                } else {
                    seekPosition = 0;
                    if (!interrupt)
                        mp.start();
                }
            }
        });
        interrupt = false;
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                seekPosition++;
                prevJumpTime = System.currentTimeMillis();
                if (seekTimes.size() > 0)
                    mp.seekTo(seekTimes.get(0));
            }
        }, startDelay);
    }

    private void takeTwoRewind(final int speed) {

    }


    private void rewind(final int speed) {
        modifiedSpeedPlayback(speed, false, false);
    }

    private void fastForward(final int speed) {
        modifiedSpeedPlayback(speed, true, false);
    }

    private void playReverseFromEnd(final int speed) {
        modifiedSpeedPlayback(speed, false, true);
    }

    private void playFastForwardFromBeginning(int speed) {
        modifiedSpeedPlayback(speed, true, true);
    }

    private void setUpSeekBar() {

        barBackground = new RelativeLayout(this.getActivity());
        RelativeLayout.LayoutParams bParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        //bParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        //barBackground.setBackgroundColor(R.color.black); //ignore error for cool effect
        bParams.topMargin = 315;

        barBackground.setLayoutParams(bParams);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
        seekBar.setLayoutParams(params);
        seekBar.setMax(100);
        seekBar.setProgress(100);
        barBackground.addView(seekBar);

        final ArrayList<Float> timeMarkers = new ArrayList<Float>();

        updateSeekBar = new Runnable() {
            @Override
            public void run() {
                synchronized (fileLock) {
                    long now = System.currentTimeMillis();
                    long start = rs.getCurrentRecordingStartTimeMillis();

                    double totalTime = videos.getDuration() + (now - start);



                    if ((int) totalTime / 1000 <= 0)
                        seekBar.setMax(1000);
                    else
                        seekBar.setMax((int) totalTime / 1000);

                    if (currentFile == null && inPresent && !swipeMode) {
                        seekBar.setProgress(seekBar.getMax());
                    }


                    tempBookmarks.clear();
                    //timeMarkers.clear();

                    for (Long l: videos.getBookmarks()){
                        tempBookmarks.add((float) ((l)/totalTime));
                    }
                    hud.updateBookmarks(tempBookmarks);

//                    for (FileEntry file : videos.files) {
//                        timeMarkers.add((float) ((file.getStartTime()) / totalTime));
//                    }
                    hud.updateTotalTime((long)totalTime);
                    if (currentFile == null && inPresent)
                        hud.updateCurrentPosition((long)totalTime);


                    if (mp != null && currentFile != null) {

                       // hud.updateTimeMarkers(timeMarkers);
                        hud.updateCurrentPosition(getCurrentPosition());

                        long mCurrentPosition = getCurrentPosition() / 1000;

                        if (currentFile.getStartTime() + currentFile.getFileDuration() >= mCurrentPosition && !currentFile.equals(videos.getTail().getFilePath()) && !swipeMode) {
                            //Log.d("UPDATE", "updating to progress: " + (int) mCurrentPosition);
                            seekBar.setProgress((int) mCurrentPosition);
                        }

                    }

                    seekBarHandler.postDelayed(updateSeekBar, 1000);
                }
            }


        };


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_media_player, container, false);
        hud = new MediaHUD(this.getActivity());
        hud.setZOrderMediaOverlay(true);
        hud.getHolder().setFormat(PixelFormat.TRANSPARENT);
        seekBar = new SeekBar(this.getActivity());
        this.setUpSeekBar();
        surfaceView = (SurfaceView) v.findViewById(R.id.media_surface);
        ViewGroup.LayoutParams layoutParams = surfaceView.getLayoutParams();
        progressBar = new ProgressBar(getActivity());

        progressBar.setVisibility(View.GONE);
        layoutParams.height = 480;
        layoutParams.width = 640;

        RelativeLayout.LayoutParams pParams = new RelativeLayout.LayoutParams(50,
                50);
        pParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT, RelativeLayout.TRUE);
        pParams.rightMargin =120;
        pParams.topMargin = 28;

        progressBar.setLayoutParams(pParams);
        surfaceView.setLayoutParams(layoutParams);

        relative = (RelativeLayout) v.findViewById(R.id.relative);
        relative.addView(new View(this.getActivity()));
        relative.addView(hud);
        relative.addView(barBackground);
        relative.addView(progressBar);
        holder = surfaceView.getHolder();


        //holder.setSizeFromLayout();

        holder.addCallback(new SurfaceHolder.Callback() {

            public void surfaceCreated(SurfaceHolder holder) {
                if (mp != null) {
                    //holder.setFixedSize(176, 144);
                    mp.setDisplay(holder);
                }
            }

            public void surfaceDestroyed(SurfaceHolder holder) {
                if (mp != null) {
                    mp.setDisplay(null);
                }
            }

            public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            }
        });
        return v;
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if(updateSeekBar != null){
            seekBarHandler.removeCallbacks(updateSeekBar);
        }
        getActivity().finish();

    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        if (mp != null) {
            if (mp.isPlaying())
                mp.stop();
            mp.release();
            mp = null;
        }

    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i2) {
        Log.e(TAG, "MediaPlayer Error: ");
        if (i == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
            Log.w(TAG, "Server Died");
            mediaPlayer.release();
            mp = null;
            createMediaPlayer();
        } else if (i == MediaPlayer.MEDIA_ERROR_UNKNOWN) {
            Log.w(TAG, "Unknown Error, resetting");
            mediaPlayer.release();
            mp = null;
            createMediaPlayer();
        }
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        Log.d("PREP", "on prepared called");
        mediaPlayer.start();
    }


    @Override
    public boolean onGesture(Gesture gesture) {
        String name = gesture.name();

        if(name.equals("TAP")) {
            handleInput("toggle",0,0);
        }
        else if (name.equals("TWO_SWIPE_RIGHT")) {
            handleInput("jumpToPresent",-10000,0);
        }
        else if (name.equals("SWIPE_RIGHT")){
            handleInput("jump",5000,0);
        }
        else if (name.equals("SWIPE_LEFT")) {
            handleInput("jump",-5000,0);
        } else if (name.equals("TWO_SWIPE_LEFT")) {
            handleInput("jumpToPresent",-10000,0);
        } else if (name.equals("TWO_TAP")) {
            handleInput("placeBookmark",0,0);
        }

        return false;
    }



    public synchronized  void onCompletion(MediaPlayer mp) {
        interrupt = false;
        long start = rs.getCurrentRecordingStartTimeMillis();
        long now = System.currentTimeMillis();
        if (now - start<1500){
            jumpToPresent();
            return;
        }

        synchronized (lock) {
            if (interrupt || inPresent) {
                return;
            }
            Log.d("COMPLETE","currently in file: ");
            FileEntry file = videos.getFileAfter(currentFile);
            Log.d("COMPLETE", "calling on completion");
            if (file == null) {
                cutTail();
                file = videos.getFileAfter(currentFile);
                Uri newUri = Uri.fromFile(new File(file.getFilePath()));
                currentFile = file;
                this.setMediaSource(newUri, false);
                seekBarHandler.post(updateSeekBar);
            } else {
                Uri newUri = Uri.fromFile(new File(file.getFilePath()));
                Log.d("COMPLETE", "file: " + file.getFilePath());
                Log.d("COMPLETE", "tail: " + videos.getTail().getFilePath());

                currentFile = file;
                this.setMediaSource(newUri, false);
                seekBarHandler.post(updateSeekBar);


            }
        }

    }

    public long getDuration(String path) {
        MediaPlayer mp = MediaPlayer.create(this.getActivity(), Uri.parse(path));
        return mp.getDuration();
    }

    public void setServiceHandle(MediaRecordingService service) {
        rs = service;
    }


}
