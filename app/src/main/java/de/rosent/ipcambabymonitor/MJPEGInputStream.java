package de.rosent.ipcambabymonitor;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EmptyStackException;
import java.util.Properties;
import java.util.Stack;
import java.util.concurrent.Semaphore;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class MJPEGInputStream extends DataInputStream {
    private static final String TAG = "MjpegInputStream";

    private final byte[] SOI_MARKER = { (byte) 0xFF, (byte) 0xD8 };
    private final byte[] EOF_MARKER = { (byte) 0xFF, (byte) 0xD9 };
    private final String CONTENT_LENGTH = "Content-Length";
    private final static int HEADER_MAX_LENGTH = 100;
    private final static int FRAME_MAX_LENGTH = 4000 + HEADER_MAX_LENGTH;
    private int mContentLength = -1;
    private Stack<byte[]> frames;
    private Semaphore stackFree = null;

    public MJPEGInputStream(InputStream in) {
        super(new BufferedInputStream(in, FRAME_MAX_LENGTH));
        frames = new Stack<byte[]>();
        stackFree = new Semaphore(1, true);
    }

    private int getEndOfSequence(DataInputStream in, byte[] sequence) throws IOException {
        int seqIndex = 0;
        byte c;
        for(int i=0; i < FRAME_MAX_LENGTH; i++) {
            c = (byte) in.readUnsignedByte();
            if(c == sequence[seqIndex]) {
                seqIndex++;
                if(seqIndex == sequence.length) {
                    return i + 1;
                }
            } else {
                seqIndex = 0;
            }
        }
        return -1;
    }

    private int getStartOfSequence(DataInputStream in, byte[] sequence) throws IOException {
        int end = getEndOfSequence(in, sequence);
        return (end < 0) ? (-1) : (end - sequence.length);
    }

    private int parseContentLength(byte[] headerBytes) throws IOException, NumberFormatException {
        ByteArrayInputStream headerIn = new ByteArrayInputStream(headerBytes);
        Properties props = new Properties();
        props.load(headerIn);
        return Integer.parseInt(props.getProperty(CONTENT_LENGTH));
    }   

    public Bitmap readMjpegFrame() throws IOException {
        mark(FRAME_MAX_LENGTH);
        int headerLen = getStartOfSequence(this, SOI_MARKER);
        reset();
        byte[] header = new byte[headerLen];
        readFully(header);
        try {
            mContentLength = parseContentLength(header);
        } catch (NumberFormatException nfe) { 
            nfe.getStackTrace();
            Log.d(TAG, "catch NumberFormatException hit", nfe);
            mContentLength = getEndOfSequence(this, EOF_MARKER); 
        }
        reset();
        byte[] frameData = new byte[mContentLength];
        skipBytes(headerLen);
        readFully(frameData);
        return BitmapFactory.decodeStream(new ByteArrayInputStream(frameData));
    }
    
    public void bufferNextFrame() throws IOException {
        mark(FRAME_MAX_LENGTH);
        int headerLen = getStartOfSequence(this, SOI_MARKER);
        reset();
        byte[] header = new byte[headerLen];
        readFully(header);
        try {
            mContentLength = parseContentLength(header);
        } catch (NumberFormatException nfe) { 
            nfe.getStackTrace();
            Log.d(TAG, "catch NumberFormatException hit", nfe);
            mContentLength = getEndOfSequence(this, EOF_MARKER); 
        }
        reset();
        byte[] frameData = new byte[mContentLength];
        skipBytes(headerLen);
        readFully(frameData);
        try {
	        stackFree.acquire();
	        frames.push(frameData);
	        stackFree.release();
        } catch (InterruptedException e) {
	    	//TODO - whattodo?
	    }
    }
    
    public byte[] getTopFrame() {
        byte[] retVal = null;
    	try {
	        stackFree.acquire();
	        retVal = frames.pop();
	        stackFree.release();
        } catch (InterruptedException e) {
	    	//TODO - whattodo?
	    } catch (EmptyStackException e) {
	    	// Don't do anything!
	    }
    	
    	return retVal;
    }
    
    public int resetFrames() {
    	int retVal = 0;
    	try {
	        stackFree.acquire();
	        retVal = frames.size();
	        frames.clear();
	        stackFree.release();
        } catch (InterruptedException e) {
	    	//TODO - whattodo?
	    }
    	return retVal;
    }
    
}