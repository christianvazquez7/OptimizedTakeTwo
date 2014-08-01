package com.example.taketwo.app;


public class FileTimeTuple {
    private final String filePath;
    private long timeInFile;

    public FileTimeTuple(String path, long time) {
        filePath = path;
        timeInFile = time;
    }

    public long getTimeInFile() {
        return timeInFile;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setTimeInFile(long time) {
        timeInFile = time;
    }

}
