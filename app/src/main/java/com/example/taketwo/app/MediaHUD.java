package com.example.taketwo.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class MediaHUD extends SurfaceView implements SurfaceHolder.Callback {

    private DrawingThread drawingThread;
    private Canvas c;
    private Bitmap pause;
    private Bitmap stop;
    private Bitmap record;
    private Bitmap skipForward;
    private Bitmap skipBack;
    private Bitmap error;
    private Bitmap play;
    private boolean isPaused=false;
    private boolean isStopped = false;
    private boolean isRecording = false;
    private boolean isSkippingForward = false;
    private boolean isSkippingBack = false;
    private boolean wasPaused = false;
    private boolean isPresent = false;
    private boolean waitingForTap = false;
    private boolean validJump = true;
    private ArrayList<Float> timeMarkers;
    private String currentPosition = "00:00";
    private String totalTime = "00:00";
    private int actionIconX;
    private int actionIconY;
    private int recIconX;
    private int recIconY;
    private int tapTextSize;
    private int currentTimeX;
    private int currentTimeY;
    private int totalTimeX;
    private int totalTimeY;
    private int timeTextSize;
    private int tapTextX;
    private int tapTextY;
    private boolean isMerging;
    private ArrayList<Float> tempMarkers;
    private ArrayList<Float> tempBookmarks= new ArrayList<Float>();
    private Object lock = new Object();
    private Object block = new Object();
    private ArrayList<Float> bookmarks = new ArrayList<Float>();

    public MediaHUD(Context context) {
        super(context);
        getHolder().addCallback(this);
        drawingThread = new DrawingThread(getHolder(), this);
        pause = BitmapFactory.decodeResource(getResources(),
                R.drawable.videopause);
        stop = BitmapFactory.decodeResource(getResources(),
                R.drawable.stop);
        record = BitmapFactory.decodeResource(getResources(),
                R.drawable.record);
        skipForward = BitmapFactory.decodeResource(getResources(),
                R.drawable.skipforward);
        skipBack = BitmapFactory.decodeResource(getResources(),
                R.drawable.skipbackward);
        error = BitmapFactory.decodeResource(getResources(),
                R.drawable.error);
        play = BitmapFactory.decodeResource(getResources(),R.drawable.play);

        timeMarkers = new ArrayList<Float>();

        actionIconX = getResources().getInteger(R.integer.action_icon_x);
        actionIconY = getResources().getInteger(R.integer.action_icon_y);
        recIconX = getResources().getInteger(R.integer.rec_icon_x);
        recIconY = getResources().getInteger(R.integer.rec_icon_y);
        tapTextSize = getResources().getInteger(R.integer.tap_text_size);
        timeTextSize = getResources().getInteger(R.integer.time_text_size);
        currentTimeX = getResources().getInteger(R.integer.current_time_x);
        currentTimeY = getResources().getInteger(R.integer.current_time_y);
        totalTimeX = getResources().getInteger(R.integer.total_time_x);
        totalTimeY = getResources().getInteger(R.integer.total_time_y);
        tapTextX = getResources().getInteger(R.integer.tap_text_x);
        tapTextY = getResources().getInteger(R.integer.tap_text_y);

    }

    public void displayMerging(boolean merging){
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                isMerging = merging;
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (canvas != null) {



            canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);


            if (isPresent) {
                c.drawColor(Color.BLACK);
            }
            if (isPaused && !isPresent) {
                canvas.drawBitmap(pause, actionIconX, actionIconY, null);
            } else if (!isPresent && !isSkippingBack && !waitingForTap && !isSkippingForward) {
                canvas.drawBitmap(play, actionIconX, actionIconY, null);
            }
            if (isStopped) {
                canvas.drawBitmap(stop, actionIconX ,actionIconY, null);
            }
            if (isRecording) {
                canvas.drawBitmap(record, recIconX, recIconY, null);
            }
            if (isSkippingBack) {
                canvas.drawBitmap(skipBack, actionIconX, actionIconY, null);
            }
            if (isSkippingForward) {
                if (!validJump) {
                    canvas.drawBitmap(skipForward, actionIconX, actionIconY, null);
                    canvas.drawBitmap(error, 550, 60, null);
                } else {
                    canvas.drawBitmap(skipForward, actionIconX, actionIconY, null);
                }
            }
            if (waitingForTap) {
                Paint paint = new Paint();
                paint.setColor(Color.WHITE);
                canvas.drawColor(Color.BLACK, PorterDuff.Mode.OVERLAY);
                paint.setTextSize(tapTextSize);
                canvas.drawText("Tap to Continue", tapTextX, tapTextY, paint);
            }
            if (isMerging) {
                Paint timePaint = new Paint();
                timePaint.setColor(Color.WHITE);
                timePaint.setTextSize(timeTextSize);
                canvas.drawText("Merging...", actionIconX-15, actionIconY+30, timePaint);
            }

            Paint tickMarkPaint = new Paint();
            tickMarkPaint.setARGB(127, 255, 255, 0);
            synchronized (lock) {
                if (tempMarkers != null) {
                    timeMarkers.clear();
                    for (Float f : tempMarkers) {
                        timeMarkers.add(f);
                    }
                }



                for (Float time : timeMarkers) {
                    canvas.drawRect(time * 570, 300, time * 570 + 5, 320, tickMarkPaint);
                }
            }
            Paint bookmarkPaint = new Paint();
            bookmarkPaint.setARGB(127, 46, 199, 0);
            synchronized (block) {
                if (tempBookmarks != null) {
                    bookmarks.clear();
                    for (Float f : tempBookmarks) {
                        bookmarks.add(f);
                    }
                }



                for (Float time : bookmarks) {
                    canvas.drawRect(time * 570, 300, time * 570 + 5, 320, bookmarkPaint);
                }
            }

            Paint timePaint = new Paint();
            timePaint.setColor(Color.WHITE);
            timePaint.setTextSize(timeTextSize);
            canvas.drawText(currentPosition, currentTimeX,currentTimeY, timePaint);
            canvas.drawText(totalTime, totalTimeX, totalTimeY, timePaint); //more to the left
        }
    }

    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3) {

    }

    public void surfaceCreated(SurfaceHolder arg0) {

        //drawingThread.setRunning(true);
        //drawingThread.start();
    }

    public void tapToContinue() {
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                waitingForTap = true ;
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }

    public void showPause() {

        this.clearSkip();

        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                isPaused= true;
                isStopped = false;
                validJump = true;
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }

    public void stopRecording() {
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                this.isRecording = false;
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }

    }

    public void clearSkip(){
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                this.isSkippingBack = false;
                this.isSkippingForward = false;
                this.isStopped = false;
                if(wasPaused)
                    isPaused = true;
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }
    public void showSkipBack() {
        wasPaused = isPaused;
        this.clear();
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                this.isSkippingForward = false;
                this.isSkippingBack = true;
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }

        Timer timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
               MediaHUD.this.clearSkip();
            }
        }, MediaPlayerFragment.jumpLimit);
    }

    public void showSwipeMode(){

    }
    public void showSkipForward(boolean valid) {
        wasPaused = isPaused;
        this.clear();
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                this.isSkippingBack = false;
                this.isSkippingForward = true;
                this.validJump = valid;
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }

        Timer timer = new Timer();

        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                MediaHUD.this.clearSkip();
            }
        },  MediaPlayerFragment.jumpLimit);
    }

    public void showRecording() {
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
               this.isRecording = true;
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }

    public void showStop() {
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                this.isPaused = false;
                isStopped = true;
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }

    public void showPresent() {
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                isPresent = true;
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }

    public void hidePresent() {
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                isPresent = false;
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }

    public void updateBookmarks(ArrayList<Float> marks) {
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                synchronized (block) { //remove
                    this.tempBookmarks = marks;
                }
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }
    public void updateTimeMarkers(ArrayList<Float> timeMarkers) {
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                synchronized (lock) { //remove
                    this.tempMarkers = timeMarkers;
                }
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }

    public void updateCurrentPosition(long currentPosition) {
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                this.currentPosition = millisToMinuteSecond(currentPosition);
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }

    public void updateTotalTime(long totalTime) {
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                this.totalTime = millisToMinuteSecond(totalTime);
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }

    private String millisToMinuteSecond(long millis) {
        return new SimpleDateFormat("mm:ss").format(new Date(millis));
    }

    public void clear() {
        try {
            c = this.getHolder().lockCanvas(null);
            synchronized (this.getHolder()) {
                isPaused = false;
                isStopped = false;
                if (isSkippingBack || isSkippingForward)
                        wasPaused = false;
                isSkippingForward = false;
                isSkippingBack = false;
                waitingForTap = false;
                this.onDraw(c);
            }
        } finally {
            if (c != null) {
                this.getHolder().unlockCanvasAndPost(c);
            }
        }
    }




    public void surfaceDestroyed(SurfaceHolder arg0) {
        boolean retry = true;
        drawingThread.setRunning(false);
        while (retry) {
            try {
                drawingThread.join();
                retry = false;
            } catch (InterruptedException e) {
            }
        }
    }

    class DrawingThread extends Thread {
        private SurfaceHolder _surfaceHolder;
        private MediaHUD _panel;
        private boolean _run = false;

        public DrawingThread(SurfaceHolder surfaceHolder, MediaHUD panel) {
            _surfaceHolder = surfaceHolder;
            _panel = panel;
        }

        public void setRunning(boolean run) {
            _run = run;
        }

        @Override
        public void run() {
            Canvas c;
            while (_run) {
                c = null;
                try {
                    c = _surfaceHolder.lockCanvas(null);
                    synchronized (_surfaceHolder) {
                        _panel.onDraw(c);
                    }
                } finally {
                    if (c != null) {
                        _surfaceHolder.unlockCanvasAndPost(c);
                    }
                }
            }
        }
    }
}
