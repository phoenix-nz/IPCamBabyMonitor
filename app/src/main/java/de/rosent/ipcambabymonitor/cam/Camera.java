package de.rosent.ipcambabymonitor.cam;

import android.content.Context;
import android.content.res.Resources;
import android.os.Handler;
import android.view.MotionEvent;
import android.widget.ImageView;

import de.rosent.ipcambabymonitor.R;

public abstract class Camera {
    protected int cameraId = 0;
    protected String cameraPort = "";
    protected String cameraHost = "";
	protected String cameraUsername = "";
    protected String cameraPassword = "";
    protected String cameraLabel = "";
    protected int cameraTypeId = -1;
	protected boolean selected = false;

	protected Context context;
    
	abstract public Camera clone();

    public static Camera createCamera(int i, String h, String p, String u, String pwd, String l, int tid, Context context) {
        switch(tid){
            case 1: return new de.rosent.ipcambabymonitor.cam.foscam.FoscamCamera(i,h,p,u,pwd,l,tid);
            default:
                final Resources resourceBundle = context.getResources();
                throw new UnsupportedOperationException(resourceBundle.getString(R.string.unknownCameraType));
        }
    }

    public Camera(int i, String h, String p, String u, String pwd, String l, int tid) {
        this.cameraId = i;
        this.cameraHost = h;
        this.cameraPort = p;
        this.cameraUsername = u;
        this.cameraPassword = pwd;
        this.cameraLabel = l;
        this.cameraTypeId = tid;
    }

    public void setContext( Context context ) {
    	this.context = context;
    }
    public void setId(int i) {
        this.cameraId = i;
    }
    
    public int getId() {
        return this.cameraId;
    }

    public void setHost(String h) {
        this.cameraHost = h;
    }

    public String getHost() {
        return this.cameraHost;
    }

    public void setPort(String p) {
        this.cameraPort = p;
    }

    public String getPort() {
        return this.cameraPort;
    }

    public void setUsername(String u) {
        this.cameraUsername = u;
    }
    
    public String getUsername() {
        return this.cameraUsername;
    }
    
    public void setPassword(String p) {
        this.cameraPassword = p;
    }
	
    public String getPassword() {
        return this.cameraPassword;
    }
    
    public void setLabel(String l) {
        this.cameraLabel = l;
    }
    
    public String getLabel() {
        return this.cameraLabel;
    }
    
    public void setTypeId(int tid) {
        this.cameraTypeId = tid;
    }
    
    public int getTypeId() {
        return this.cameraTypeId;
    }

	public void setSelected(boolean selected) {
		this.selected = selected;
	}
	
	public boolean getSelected() {
		return this.selected;
	}

    abstract public void    connectToCam();

	abstract public void stop( boolean keepAudioRunning);

    abstract public boolean supportsAudio();
    abstract public void    streamAudio();
    abstract public void    stopAudio( );
    abstract public boolean isStreamingAudio();

    abstract public boolean supportsMic();
    abstract public void    streamMic( );
    abstract public void    stopMic( );
    abstract public boolean isStreamingMic();

    abstract public boolean isStreamingVideo();
    abstract public boolean streamVideo(ImageView iStream, Handler videoHandler);
    abstract public void    stopVideo( );
    abstract public boolean flipImage(boolean vertical, boolean horizontal);

    abstract public boolean supportsAlarm();
    abstract public boolean isAlarm();
    abstract public Alarm   getAlarm();

    abstract public boolean supportsIR();
    abstract public void    setIR(boolean on);
    abstract public boolean getIR();

    abstract public boolean supportsMove();
    abstract public void    move(MotionEvent e);

}
