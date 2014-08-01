package com.example.taketwo.app;


import android.content.Context;
import android.os.Handler;
import android.util.Log;
import android.util.Pair;
import android.view.SurfaceView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class TakeTwoMediaRecorder {

    private AudioRecordThread aRecorder;
    private Muxdec muxdec;
    private final static String TAG = "TakeTwoMediaRecorder";
    private CameraHandlerThread cameraThread;
    private Runnable poll;
    private Handler recordingHandler = new Handler();
    private  static final String DUMMY_FILE = "/sdcard/muxi.mp4";
    private int fileNumber = 0;
    private long currentRecordingStartTimeMillis;
    private String currentFile = "";
    private boolean interrupt = false;
    Thread run;


    public TakeTwoMediaRecorder (Context activity, SurfaceView sv) {
        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
        currentFile = this.generateFileName();
        muxdec = new Muxdec(currentFile);
        cameraThread = new CameraHandlerThread(sv);
        cameraThread.openCamera();
        aRecorder = new AudioRecordThread(activity,"/sdcard/dummy");
    }

    public String start() {
        currentRecordingStartTimeMillis = System.currentTimeMillis();
        while(!cameraThread.startPreview());
        aRecorder.start();
        run = new Thread(new Runnable() {
            @Override
            public void run() {
            Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                while (!interrupt) {
                    Pair<Long, byte[]> videoBuffer = cameraThread.getFrame();
                    Pair<Long, byte[]> audioBuffer = aRecorder.getFrame();
                    if (audioBuffer != null) {
                        muxdec.offerAudioEncoder(audioBuffer);
                    }
                    if (videoBuffer != null) {
                        muxdec.offerEncoder(videoBuffer);
                    }

                }
                Log.d("EXIT","Exiting thread, safe to continue");
            }
        });
        run.start();
        return currentFile;
    }

    public void release() {
        cameraThread.releaseCamera();
    }

    public String stop(boolean continueRecording) {
        interrupt= true;
        try {
            run.join();
        } catch (Exception e){}
            run.interrupt();
        if (continueRecording)
            currentRecordingStartTimeMillis = System.currentTimeMillis();

        int vSize = cameraThread.size();
        int aSize = aRecorder.size();
        int size = Math.max(aSize, vSize);
        Log.d(TAG,"max: "+size);
        Log.d(TAG,"cam: "+vSize);
        Log.d(TAG,"audio: "+aSize);
        Muxdec oldMux = muxdec;
        String fileName = generateFileName();

        if (!continueRecording) {
            cameraThread.stopPreview();
            aRecorder.stopRecording();
        } else {
                muxdec = new Muxdec(fileName);
                interrupt = false;
                run = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                        while (!interrupt) {
                            Pair<Long, byte[]> videoBuffer = cameraThread.getFrame();
                            Pair<Long, byte[]> audioBuffer = aRecorder.getFrame();
                            if (audioBuffer != null) {
                                muxdec.offerAudioEncoder(audioBuffer);
                            }
                            if (videoBuffer != null) {
                                muxdec.offerEncoder(videoBuffer);
                            }

                        }
                    }
                });
            for (int i = 0 ; i <size; i++) {
                if (i < vSize) {
                    oldMux.offerEncoder(cameraThread.getFrame());
                }
                if (i < aSize) {
                    oldMux.offerAudioEncoder(aRecorder.getFrame());
                }
            }
                run.start();
        }


        Log.d("HAHA","Left vid:"+cameraThread.size());
        Log.d("HAHA","Left audio:"+aRecorder.size());


        oldMux.disconnect();

        if (continueRecording) {
            return fileName;
        }
        return null;
    }



    private String generateFileName() {
        File mediaStorageDir = new File("/sdcard/", "wearscript_video");
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "failed to create directory");
            }
        }
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        return mediaStorageDir.getPath() + File.separator + timeStamp + ".mp4";
    }

    public long getCurrentRecordingStartTimeMillis() {
        return currentRecordingStartTimeMillis;
    }

}
