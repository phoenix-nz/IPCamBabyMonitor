package de.rosent.ipcambabymonitor.cam.easyn.foscam;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import javax.security.auth.login.LoginException;

import de.rosent.ipcambabymonitor.R;

public class EasyNInputStream extends DataInputStream {
    private static final String TAG = "EasyNInputStream";

    private final static int HEADER_MAX_LENGTH = 23;
    private final static int FRAME_MAX_LENGTH = 4000 + HEADER_MAX_LENGTH;
    private int mContentLength = -1;
    private Context context; //required only to get translatable strings???
    
    private byte[] connectionID = new byte[4];
    
    private boolean	alarmActive = false;
    private int      	alarmType   = 0;
    private long      	lastAlarmTime = 0;
    
    public EasyNInputStream(InputStream in, Context context) {
        super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
        this.context = context;
    }  

    /*  Weird method that returns some form of EasyNUtil variable
     *  to indicate what kind of frame has been read.
     *  
     *  The calling function can then try and do something with the
     *  returned frame, by calling follow on functions. 
     */
    public int readFrame() throws IOException, LoginException {
    	return readFrame(false);
    }
    public int readFrame(boolean onlyCheck) throws IOException, LoginException {
        if(onlyCheck && this.available() == 0) return -2;
    	mark(FRAME_MAX_LENGTH);
        reset();
        byte[] header = new byte[EasyNUtil.HeaderSize];
        try {
        	readFully(header);
        } catch ( EOFException e ) {
        	// Do nothing.
        }
        try {
            mContentLength = EasyNUtil.parseContentLength(header);
        } catch (NumberFormatException nfe) {
            nfe.getStackTrace();
            Log.d(TAG, "catch NumberFormatException hit", nfe);
            mContentLength = 0;
        }
        byte[] frameData = null;
        if (mContentLength > 0) {
        	reset();
        	frameData = new byte[mContentLength];
        	skipBytes(EasyNUtil.HeaderSize);
        	readFully(frameData);
        }
        int retVal = parseFrame( header, frameData);
        while ( retVal == EasyNUtil.KeepAlive || retVal == EasyNUtil.NotifyAlarm ) {
        	if( retVal == EasyNUtil.NotifyAlarm) {
        		if(frameData[0] == 0) 	alarmActive = false;
        		else 				   	alarmActive = true;

        		alarmType = frameData[0];
        		lastAlarmTime = System.currentTimeMillis() + 30000;
        		mark(FRAME_MAX_LENGTH);
        	} else if (alarmActive && lastAlarmTime > System.currentTimeMillis()) {
        		alarmActive = false;
        	}
        	retVal = readFrame(onlyCheck);
        }
        if(onlyCheck) reset();
        return retVal;
    }

    private int parseFrame( byte[] header, byte[] data) throws LoginException {
    	int retVal = -2;
    	if ( header[3] == EasyNUtil.MO_O[3]) {
    		// parse a command
	    	switch(header[EasyNUtil.CommandPos]) {
	    	case EasyNUtil.RespReqLogin:
	    		if ( data[0] == 0 ) {
	    			// Login request ok
	    			// TODO store information like CamId?
	    			retVal = header[EasyNUtil.CommandPos];
	    		} else {
	    			// Cannot login
	    			throw new LoginException(context.getResources().getString(R.string.MaxConnections));
	    		}
	    		break;
	    	case EasyNUtil.RespAuthLogin:
	    		if ( data[0] == 0 ) {
	    			// Login ok
	    			retVal = header[EasyNUtil.CommandPos];
	    		} else if ( data[0] == 1) {
	    			// Cannot login
	    			throw new LoginException(context.getResources().getString(R.string.UnknownUser));
	    		} else if ( data[0] == 5) {
	    			// Cannot login
	    			throw new LoginException(context.getResources().getString(R.string.UnknownPassword));
	    		}
	    		break;
	    	case EasyNUtil.RespStartVideo:
	    	case EasyNUtil.RespStartAudio:
	    	case EasyNUtil.RespStartTalk:
	        	if (data[0] == 0) {
	    			// check if a connection ID exists:
	        		for(int i = 2; i < data.length; i++)
	        			this.connectionID[i-2] = data[i];
	        		retVal = header[EasyNUtil.CommandPos];
	    		} else {
	    			throw new LoginException(context.getResources().getString(R.string.MaxConnections));
	    		} // TODO RespStartTalk also has a 7 return code - needs a different error...
	    		break;
	    	case EasyNUtil.RespUNKNOWN_2:
	    		// TODO - what do we do with this?
	    		break;
	    	case EasyNUtil.NotifyAlarm:
	    		retVal = header[EasyNUtil.CommandPos];
	    		break;
	    	default:
	    		retVal = header[EasyNUtil.CommandPos];
	    	}
    	}
	    else { //if (header[3] == EasyNUtil.MO_V[3] )
	    	//parse data - currently only audio
	    	// TODO video implementation of parsing?
	    	if( header[EasyNUtil.CommandPos] == 2 ) {
	    		// TODO audio implementation of parsing.
	    	}
	    	
	    }
    	return retVal;
    }

    public byte[] getConnectionID() {
    	return this.connectionID;
    }
    
    public boolean getAlarmActive() {
    	try {
    		readFrame(true);
    	} catch (IOException e) {
    		
    	} catch (LoginException e) {
    		
    	}
    	return this.alarmActive;
    }
    public int getAlarmType() {
    	return this.alarmType;
    }
}
