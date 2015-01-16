package de.rosent.ipcambabymonitor.cam;

/**
 * Created by robin on 16.01.15.
 */
public class Alarm {
    private String text;

    public Alarm(String text){
        this.text = text;
    }

    public String getText( ) {
        return text;
    }
}
