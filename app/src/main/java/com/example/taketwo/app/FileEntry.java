package com.example.taketwo.app;


public class FileEntry {
    private  String filePath;
    private long fileDuration;
    private long startTime;

    public FileEntry(String filePath, long startTime, long fileDuration) {
        this.filePath = filePath;
        this.startTime = startTime;
        this.fileDuration = fileDuration;
    }

    public void setName(String name){
        filePath= name;
    }
    public String getFilePath() {
        return filePath;
    }

    public long getFileDuration() {
        return fileDuration;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setFileDuration (long duration) {
        fileDuration = duration;
    }

    public void setStartTime (long startTime) {
        this.startTime = startTime;
    }
}
