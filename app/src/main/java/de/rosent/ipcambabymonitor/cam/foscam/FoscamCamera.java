package de.rosent.ipcambabymonitor.cam.foscam;

import de.rosent.ipcambabymonitor.cam.Alarm;
import de.rosent.ipcambabymonitor.cam.Camera;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.concurrent.Semaphore;

import javax.security.auth.login.LoginException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Handler;
import android.view.MotionEvent;
import android.widget.ImageView;

import de.rosent.ipcambabymonitor.cam.MJPEGInputStream;
import de.rosent.ipcambabymonitor.R;

/**
 * Created by robin on 16.01.15.
 */
public class FoscamCamera extends Camera {
    private int cameraTypeId = 1;
    private boolean selected = false;
    private boolean showDropped = false;
    private boolean vFlip = false;
    private boolean hFlip = false;


    private Thread  		  	videoThread = null;
    private Handler 		  	videoHandler = null;
    private MJPEGInputStream videoReader = null;
    private boolean 		  	isStreamingVideo = false;
    private boolean		  	isPosting = false;
    private ImageView 		  	iStream;
    private Bitmap 		  	bmp;
    private Paint			  	overlayPaint = null;
    private byte            	showOverlayPlaying = 60; //show overlay for a few seconds on startup
    private BitmapFactory.Options overlayOptions = null;
    private int				scaleFactor;

    private MotionEvent currentMove = null;
    private boolean	 isMoving    = false;
    private int		 xCenter = 0;
    private int		 yCenter = 0;
    private Thread		 moveThread = null;
    private static int MOVE_IGNORE = 40;

    private Socket controlConnection;
    private Semaphore controlFree;
    private OutputStream controlOut;
    private FoscamInputStream controlIn;
    private FoscamAudioListener audioListener = null;
    private FoscamAudioSender audioSender = null;

    private boolean connectionInitiated = false;

    private Context context;

    public Camera clone() {
        FoscamCamera c = new FoscamCamera(this.cameraId, this.cameraHost, this.cameraPort, this.cameraUsername, this.cameraPassword, this.cameraLabel, this.cameraTypeId);
        c.videoThread = this.videoThread;
        c.videoHandler = this.videoHandler;
        c.videoReader= this.videoReader;
        c.isStreamingVideo = this.isStreamingVideo;
        c.isPosting = this.isPosting;
        c.iStream = this.iStream;
        c.bmp = this.bmp;
        c.controlConnection = this.controlConnection;
        c.controlFree = this.controlFree;
        c.controlOut = this.controlOut;
        c.controlIn = this.controlIn;
        c.audioListener = this.audioListener;
        c.audioSender = this.audioSender;
        return c;
    }

    public FoscamCamera(int i, String h, String p, String u, String pwd, String l, int tid) {
        super(i,h, p, u, pwd, l, tid);

        controlFree = new Semaphore(1, true);
    }

    public void move(MotionEvent e) {
        this.currentMove = e;
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            isMoving = true;
        } else if (e.getAction() == MotionEvent.ACTION_UP
                ||  e.getAction() == MotionEvent.ACTION_CANCEL) {
            isMoving = false;
            this.currentMove = null;
        }
        if(!isMoving) return;

        if(moveThread == null || !moveThread.isAlive()) {
            moveThread = new Thread(moveControl);
            moveThread.start();
        }
    }

    final Runnable keepAlive = new Runnable() {
        public void run() {
            while (connectionInitiated && controlOut != null) {
                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    return;
                }
                byte[] send = FoscamUtil.getCommand(FoscamUtil.KeepAlive);
                try {
                    controlFree.acquire();
                    controlOut.write(send);
                    controlOut.flush();
                } catch (IOException e) {
                    // TODO Proper Error Handling
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                controlFree.release();
            }
        }
    };

    private synchronized void runUpdateUI() {
        if(isPosting) return;
        else isPosting = true;
        videoHandler.post(updateUI);
    }

    final Runnable moveControl = new Runnable() {
        public void run() {
            int right = (int)currentMove.getX() - xCenter;
            if(hFlip) right = -1*right;
            int down  = (int)currentMove.getY() - yCenter;
            if(vFlip) down = -1*down;
            if (Math.abs(right) < FoscamCamera.MOVE_IGNORE && Math.abs(down) < FoscamCamera.MOVE_IGNORE && isStreamingVideo) {
                stopVideo();
                runUpdateUI();
            } else {
                int count = 10;
                showOverlayPlaying = 10;
                while(count != 0 && isMoving && currentMove != null && currentMove.getPointerCount() != 0) {
                    count--;
                    URL myFileURL = null;
                    InputStream controlStream;
                    String url = "";
                    String command ="";
                    if (Math.abs(down) > FoscamCamera.MOVE_IGNORE) {
                        if (down < 0) {
                            command = "0";
                        } else if ( down > 0 ){
                            command = "2";
                        }
                        url = cameraHost + ":" + cameraPort + "/decoder_control.cgi?command=" + command + "&onestep=1&user=" + cameraUsername + "&pwd=" + cameraPassword;
                        try {
                            myFileURL = new URL(url);
                            controlStream = myFileURL.openStream();
                            //controlStream.read();
                            controlStream.close();
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }
                    }
                    if (Math.abs(right) > FoscamCamera.MOVE_IGNORE) {
                        if( right < 0) {
                            command = "6";
                        } else if (right > 0){
                            command = "4";
                        }
                        url = cameraHost + ":" + cameraPort + "/decoder_control.cgi?command=" + command + "&onestep=1&user=" + cameraUsername + "&pwd=" + cameraPassword;
                        try {
                            myFileURL = new URL(url);
                            controlStream = myFileURL.openStream();
                            //controlStream.read();
                            controlStream.close();
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
            }
        }
    };


    public boolean streamVideo(ImageView iStream, Handler videoHandler) {
        if (isStreamingVideo) return true;

        if (overlayPaint == null ) {
            overlayPaint = new Paint();
            overlayPaint.setTextAlign(Paint.Align.LEFT);
            overlayPaint.setTextSize(12);
            overlayPaint.setTypeface(Typeface.DEFAULT);
        }

        this.iStream = iStream;
        String videoUrl = cameraHost + ":" + cameraPort + "/videostream.cgi?user=" + cameraUsername + "&pwd=" + cameraPassword;
        //int width = iStream.getWidth();
        if( iStream.getWidth() < 600 ) {
            videoUrl += "&resolution=16";
            scaleFactor = 2;
        } else {
            videoUrl += "&resolution=32";
            scaleFactor = 1;
        }
        try {
            DefaultHttpClient httpClient = new DefaultHttpClient();
            HttpResponse res = httpClient.execute(new HttpGet(URI.create(videoUrl)));
            InputStream videoStream = res.getEntity().getContent();
            videoReader = new MJPEGInputStream(videoStream);
        } catch (Exception e) {
//			Toast toast = Toast.makeText(iStream, e.getMessage(), Toast.LENGTH_LONG);
            //toast.show();
            // System.out.println(e.getMessage());
            return false;
        }

        overlayOptions = new BitmapFactory.Options();

        videoThread = new Thread(streamControl);
        if (!isStreamingVideo) {
            videoThread.start();
        }

        this.videoHandler = videoHandler;

        return isStreamingVideo;
    }

    public void stop( boolean keepAudioRunning) {
        this.stopVideo();
        this.stopMic();
        if (!keepAudioRunning)
            this.stopAudio();
    }

    @Override
    public boolean supportsAudio() {
        return true;
    }


    final Runnable streamControl = new Runnable() {
        public void run() {
            isStreamingVideo = true;
            while(isStreamingVideo) {
                try {
                    videoReader.bufferNextFrame();
                    //bmp = videoReader.readMjpegFrame();
                } catch (IOException e) {
                    //TODO - Error here
                    isStreamingVideo = false;
                }
                runUpdateUI();
            }
        }
    };

    final Runnable updateUI = new Runnable() {
        public void run() {
            byte[] topFrame = videoReader.getTopFrame();
            if( topFrame != null) {
                bmp = BitmapFactory.decodeStream(new ByteArrayInputStream(topFrame));
                if( bmp != null ) {
                    if(showDropped){
                        int dropped = videoReader.resetFrames();
                        Bitmap overlay = createFramesOverlay( dropped );
                        bmp = combineBmps(bmp, overlay, false);
                    }
                    if(showOverlayPlaying > 0) {
                        overlayOptions.inSampleSize = scaleFactor;
                        Bitmap overlay = BitmapFactory.decodeResource(context.getResources(), R.drawable.overlay_playing, overlayOptions);
                        bmp = combineBmps(bmp, overlay, true);
                        showOverlayPlaying--;
                    }
                    iStream.setImageBitmap(bmp);
                    if ( xCenter == 0 ) {
                        xCenter = iStream.getLeft()+iStream.getWidth()/2;
                        yCenter = iStream.getBottom()-iStream.getHeight()/2;
                    }
                    videoReader.resetFrames();
                } else if (!isStreamingVideo) {
                    overlayOptions.inSampleSize = scaleFactor;
                    bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.overlay_paused, overlayOptions);
                    iStream.setImageBitmap(bmp);
                }
            } else if (!isStreamingVideo) {
                overlayOptions.inSampleSize = scaleFactor;
                bmp = BitmapFactory.decodeResource(context.getResources(), R.drawable.overlay_paused, overlayOptions);
                iStream.setImageBitmap(bmp);
            }
            isPosting = false;
        }

        private Bitmap createFramesOverlay(int numDropped) {
            String text = "dropped:" + numDropped;
            Rect b = new Rect();
            overlayPaint.getTextBounds(text, 0, text.length(), b);
            int bwidth  = b.width()+2;
            int bheight = b.height()+2;
            Bitmap bm = Bitmap.createBitmap(bwidth, bheight, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bm);
            overlayPaint.setColor(Color.BLACK);
            c.drawRect(0, 0, bwidth, bheight, overlayPaint);
            overlayPaint.setColor(Color.WHITE);
            c.drawText(text, -b.left+1, (bheight/2)-((overlayPaint.ascent()+overlayPaint.descent())/2)+1, overlayPaint);
            return bm;
        }

        private Bitmap combineBmps(Bitmap bmp1, Bitmap bmp2, boolean center) {
            Bitmap bmOverlay = Bitmap.createBitmap(bmp1.getWidth(), bmp1.getHeight(), bmp1.getConfig());
            Canvas canvas = new Canvas(bmOverlay);
            canvas.drawBitmap(bmp1, new Matrix(), null);
            if(center) {
                int diffX = (bmp1.getWidth() - bmp2.getWidth())/2;
                int diffY = (bmp1.getHeight() - bmp2.getHeight())/2;
                canvas.drawBitmap(bmp2,  diffX, diffY, null);
            } else
                canvas.drawBitmap(bmp2, 0, 0, null);
            return bmOverlay;
        }
    };

    public boolean isStreamingAudio() {
        if (audioListener != null)
            return audioListener.isAlive();
        else
            return false;
    }

    @Override
    public boolean supportsMic() {
        return false;
    }

    public boolean isStreamingMic() {
        if (audioSender != null)
            return audioSender.isAlive();
        else
            return false;
    }

    public boolean isStreamingVideo() {
        return isStreamingVideo;
    }

    public boolean isAlarm() {
        if( !connectionInitiated)
            connectToCam();
        boolean retVal = connectionInitiated;
        if (connectionInitiated){
            try {
                controlFree.acquire();
                retVal = controlIn.getAlarmActive();
                controlFree.release();
            } catch (InterruptedException e) {
                // Don't care...
            }
        }
        return retVal;
    }

    @Override
    public Alarm getAlarm() {
        switch(controlIn.getAlarmType()) {
            case 0: return null;
            case 1: return new Alarm( context.getResources().getString(R.string.notifyMoveAlarm));
            case 2: return new Alarm( context.getResources().getString(R.string.notifyAudioAlarm));
            default: return  new Alarm( context.getResources().getString(R.string.notifyAlarm));
        }
    }

    @Override
    public boolean supportsIR() {
        return false;
    }

    public void streamAudio( ) {
        if ( !connectionInitiated ) {
            connectToCam();
        }
        try {
            controlFree.acquire();
        } catch (InterruptedException e) {
            return;
        }
        byte [] send;
        try {
            send = FoscamUtil.getCommand(FoscamUtil.StartAudio);
            controlOut.write(send);
            controlOut.flush();
            // Skip any additional frames...
            while( controlIn.readFrame() != FoscamUtil.RespStartAudio ) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // TODO do something;
                }
            }
            send = FoscamUtil.getCommence(controlIn.getConnectionID());


            String host = cameraHost;
            if (host.startsWith("http://"))
                host = host.substring(7);
            else if (host.startsWith("https://"))
                host = host.substring(8);

            Socket audioConnection = new Socket( host, Integer.parseInt(cameraPort) );
            OutputStream audioOut = audioConnection.getOutputStream();
            audioOut.write(send);
            audioOut.flush();

            audioListener = new FoscamAudioListener( audioConnection.getInputStream() );
            audioListener.start();
        } catch (IOException e) {
            // TODO Proper Error Handling
            e.printStackTrace();
        } catch (LoginException e) {
            // TODO Proper Error Handling
            e.printStackTrace();
        }

        controlFree.release();
    }

    public void streamMic( ) {
        if ( !connectionInitiated ) {
            connectToCam();
        }
        try {
            controlFree.acquire();
        } catch (InterruptedException e) {
            return;
        }
        byte [] send;
        try {
            send = FoscamUtil.getCommand(FoscamUtil.StartTalk);
            controlOut.write(send);
            controlOut.flush();
            // Skip any additional frames...
            while( controlIn.readFrame() != FoscamUtil.RespStartTalk ) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    // TODO do something;
                }
            }
            send = FoscamUtil.getCommence(controlIn.getConnectionID());


            String host = cameraHost;
            if (host.startsWith("http://"))
                host = host.substring(7);
            else if (host.startsWith("https://"))
                host = host.substring(8);

            Socket audioConnection = new Socket( host, Integer.parseInt(cameraPort) );
            OutputStream audioOut = audioConnection.getOutputStream();
            audioOut.write(send);
            audioOut.flush();

            audioSender = new FoscamAudioSender( audioOut );
            audioSender.start();
        } catch (IOException e) {
            // TODO Proper Error Handling
            e.printStackTrace();
        } catch (LoginException e) {
            // TODO Proper Error Handling
            e.printStackTrace();
        }
        controlFree.release();
    }

    public void stopAudio( ) {
        if ( connectionInitiated ) {
            byte[] send;
            send = FoscamUtil.getCommand(FoscamUtil.StopAudio);
            try {
                controlFree.acquire();
                controlOut.write(send);
                controlOut.flush();
            } catch (IOException e) {
                // TODO Proper Error Handling
                e.printStackTrace();
            } catch (InterruptedException e) {

            }
            controlFree.release();
        }
        if (audioListener != null ) {
            audioListener.stopAudio();
            audioListener.interrupt();
            audioListener = null;
        }
    }


    public void stopMic( ) {
        if ( connectionInitiated ) {
            byte[] send;
            send = FoscamUtil.getCommand(FoscamUtil.StopTalk);
            try {
                controlFree.acquire();
                controlOut.write(send);
                controlOut.flush();
            } catch (IOException e) {
                // TODO Proper Error Handling
                e.printStackTrace();
            } catch (InterruptedException e) {

            }
            controlFree.release();
        }
        if (audioSender != null ) {
            audioSender.stopAudio();
            audioSender.interrupt();
            audioSender = null;
        }
    }

    public void stopVideo( ) {
        if (videoThread != null) {
            isStreamingVideo = false;
            videoThread.interrupt();
            videoThread = null;
        }
    }


    public void setIR(boolean on) {
        if(!connectionInitiated)
            connectToCam();
        if(connectionInitiated) {
            try {
                controlFree.acquire();
            } catch (InterruptedException e) {
                return;
            }
            byte[] send = FoscamUtil.getCommand(FoscamUtil.IRSwitch);

            if (on) 	send[send.length-1] = FoscamUtil.IROn;
            else		send[send.length-1] = FoscamUtil.IROff;
            try {
                controlOut.write(send);
                controlOut.flush();
            } catch (IOException e) {
                // TODO Proper Error Handling
                e.printStackTrace();
            }

            controlFree.release();
        }
    }

    public boolean getIR() {
        String irUrl = cameraHost + ":" + cameraPort + "/get_misc.cgi?user=" + cameraUsername + "&pwd=" + cameraPassword;
        try {
            DefaultHttpClient httpclient = new DefaultHttpClient();
            HttpResponse res = httpclient.execute(new HttpGet(URI.create(irUrl)));
            BufferedReader resReader = new BufferedReader(new InputStreamReader(res.getEntity().getContent()));
            String line;
            while ((line = resReader.readLine()) != null) {
                if( line.contains("led_mode=0")) return false;
                else if (line.contains("led_mode=1")) return true;
            }
        } catch (Exception e) {
            //Toast toast = Toast.makeText(iStream, e.getMessage(), Toast.LENGTH_LONG);
            //toast.show();
            // System.out.println(e.getMessage());
            return false;
        }
        return false;
    }

    @Override
    public boolean supportsMove() {
        return false;
    }

    public boolean flipImage(boolean vertical, boolean horizontal) {
        this.vFlip = vertical;
        this.hFlip = horizontal;
        int flipMode = 0;
        if(vertical) flipMode++;
        if(horizontal) flipMode+=2;
        String irUrl = cameraHost + ":" + cameraPort + "/camera_control.cgi?user=" + cameraUsername + "&pwd=" + cameraPassword + "&param=5&value=" + flipMode;
        try {
            DefaultHttpClient httpclient = new DefaultHttpClient();
            HttpResponse res = httpclient.execute(new HttpGet(URI.create(irUrl)));
            BufferedReader resReader = new BufferedReader(new InputStreamReader(res.getEntity().getContent()));
            String line;
            while ((line = resReader.readLine()) != null) {
                if( line.contains("ok.")) return true;
            }
        } catch (Exception e) {
            //Toast toast = Toast.makeText(iStream, e.getMessage(), Toast.LENGTH_LONG);
            //toast.show();
            // System.out.println(e.getMessage());
            return false;
        }

        return false;
    }

    @Override
    public boolean supportsAlarm() {
        return false;
    }


    public void connectToCam() {
        if(connectionInitiated) return;
        try {
            controlFree.acquire();
        } catch (InterruptedException e) {
            return;
        }
        byte[] send;
        String host = cameraHost;
        if (host.startsWith("http://"))
            host = host.substring(7);
        else if (host.startsWith("https://"))
            host = host.substring(8);
        try {
            controlConnection = new Socket( host, Integer.parseInt(cameraPort) );
            controlOut = controlConnection.getOutputStream();
            controlIn = new FoscamInputStream( controlConnection.getInputStream(), this.context);
        } catch (NumberFormatException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (UnknownHostException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }  catch (NullPointerException e) {
            e.printStackTrace();
            return;
        }
        try {
            send = FoscamUtil.getCommand(FoscamUtil.ReqLogin);
            controlOut.write(send);
            controlOut.flush();

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        try {
            if (controlIn.readFrame() == FoscamUtil.RespReqLogin ) {
                send = FoscamUtil.getCommand(FoscamUtil.AuthLogin);
                byte[] tmp;
                // TODO deal with username/password longer than 13 chars
                tmp = this.cameraUsername.getBytes();
                for (int i = 0; i < tmp.length; i++) {
                    send[i + 23] = tmp[i];
                }
                tmp = this.cameraPassword.getBytes();
                for (int i = 0; i < tmp.length; i++) {
                    send[i + 36] = tmp[i];
                }
                controlOut.write(send);
                controlOut.flush();
                if (controlIn.readFrame() == FoscamUtil.RespAuthLogin) {
                    // do nothing??? we are done...
                    this.connectionInitiated = true;

                    // Now we throw out some random packets.
                    send = FoscamUtil.getCommand(FoscamUtil.UNKNOWN_1);
                    controlOut.write(send);
                    controlOut.flush();


                    controlIn.readFrame();
                    //		    	controlIn.readFrame();

                    Thread keepAliveT = new Thread(keepAlive);
                    keepAliveT.start();
                }
            }
        } catch (LoginException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        controlFree.release();
    }
}
