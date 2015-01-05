package de.rosent.ipcambabymonitor;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

public class FoscamAudioListener extends Thread {
    private InputStream stream;
    private AudioTrack player;
	private boolean running;
	private static final int AUDIO_BUFFER = 2560;
	private int mContentLength = 0;
	@SuppressWarnings("unused")
	private int camTime = -1;
	
	private int[] ima_step_table = new int[] { 
			  7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 
			  19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 
			  50, 55, 60, 66, 73, 80, 88, 97, 107, 118, 
			  130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
			  337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
			  876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 
			  2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
			  5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 
			  15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767 
			};
    
	private int[] ima_index_table = new int[] {
			  -1, -1, -1, -1, 2, 4, 6, 8,
			  -1, -1, -1, -1, 2, 4, 6, 8
			};
	
    public FoscamAudioListener( InputStream stream ) {
    	super();
    	this.stream = stream;
    	int bufferSize = AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
    	if ( bufferSize < AUDIO_BUFFER ) bufferSize = AUDIO_BUFFER;
    	player = new AudioTrack( AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM );
    }
    
	public void run() {
		int predictor = 0;
    	int stepIndex = 0;
    	int step = 0;
    	byte val = 0;
    	int high = 0;
    	int low  = 0;
    	int sign = 0;
    	int delta = 0;
    	int diff = 0;
    	int audioIndex = 0;
    	short[] audioData;
    	boolean skipPacket = false;

		running = true;
        while(running) {
        	byte[] header = new byte[FoscamUtil.HeaderSize];
            try {
            	stream.read(header);
            	//clear buffer if we are too slow...
            	if(stream.available() >= 2000) {
            		skipPacket = true;
                	System.out.println( "AudioBuffer: " + stream.available() );
            	} else
            		skipPacket = false;
            } catch ( EOFException e ) {
            	// Do nothing.
            } catch ( IOException e ) {
            	stopAudio();
            	return;
            }
            try {
                mContentLength = FoscamUtil.parseContentLength(header);
            } catch (NumberFormatException nfe) { 
                mContentLength = 0; 
            }
            byte[] frameData = null;
            if (mContentLength > 0) {
            	try {
		            frameData = new byte[mContentLength];
		            stream.read(frameData);
			     } catch (IOException e) {
		        	  stopAudio();
		        	  frameData = null;
			     }
            }
            
            
            if(frameData != null) {
            	// We have to remove all of the preamble
            	// First four bytes = timestamp
            	//frameData[0-3];
            	// Next four bytes = sequence number
            	//frameData[4-7];
            	// The the camera timestamp - this could be useful later...
            	byte[] tempTime = new byte[4];
            	for (int i = 0; i < 4; i++)
            		tempTime[3-i] = frameData[8+i];
            	this.camTime = ByteBuffer.wrap(tempTime).getInt();
            	// Make sure we are using ADPCM
            	if (frameData[12] != 0) {
            		// TODO useful message through to user?
            		stopAudio();
            		return;
            	}
            	// The next four bytes are always 160 - length of audio
            	//frameData[13-16];
            	
            	audioData = new short[(frameData.length-17)*2];
            	
            	for(int i = 17; i < frameData.length; i++) {
            		val = frameData[i];
            		high = (val >> 4) & 0x0f;
            		low = val & 0x0f;
            		
            		// runthrough for high
            		step = ima_step_table[stepIndex];
            		
            		sign = high & 0x08;
            		delta = high & 0x07;
            		diff = step >> 3;
            		if ((delta & 4) != 0) diff += step;
            		if ((delta & 2) != 0) diff += (step >> 1);
            		if ((delta & 1) != 0) diff += (step >> 2);
            		if (sign != 0)        predictor -= diff;
            	    else                  predictor += diff; 
            		//diff = ((signed)nibble + 0.5) * step / 4
            		//predictor = predictor + diff
            		if (predictor > Short.MAX_VALUE) predictor = Short.MAX_VALUE;
            		else if (predictor < Short.MIN_VALUE) predictor = Short.MIN_VALUE;
            		
            		stepIndex += ima_index_table[high];
            		if (stepIndex > 88) stepIndex = 88;
            		else if (stepIndex < 0) stepIndex = 0;
            		
            		audioData[audioIndex] = (short) predictor;
            		audioIndex++;
            		
            		// runthrough for low
            		step = ima_step_table[stepIndex];
            		
            		sign = low & 0x08;
            		delta = low & 0x07;
            		diff = step >> 3;
            		if ((delta & 4) != 0) diff += step;
            		if ((delta & 2) != 0) diff += (step >> 1);
            		if ((delta & 1) != 0) diff += (step >> 2);
            		if (sign != 0)	predictor -= diff;
            	    else 			predictor += diff; 
            		//diff = ((signed)nibble + 0.5) * step / 4
            		//predictor = predictor + diff
            		if (predictor > Short.MAX_VALUE) predictor = Short.MAX_VALUE;
            		else if (predictor < Short.MIN_VALUE) predictor = Short.MIN_VALUE;
            		
            		stepIndex += ima_index_table[low];
            		if (stepIndex > 88) stepIndex = 88;
            		else if (stepIndex < 0) stepIndex = 0;

            		audioData[audioIndex] = (short) predictor;
            		audioIndex++;
            		
            	}
            	try {
	            	if (!skipPacket)
		            	player.write(audioData, 0, audioIndex);
            		audioIndex = 0;
	               	if (player.getPlayState() != AudioTrack.PLAYSTATE_PLAYING)
		           		player.play();
	            } catch ( IllegalStateException e ) {
		           	stopAudio();
	            }
            }
        }
    }
	
	public void stopAudio() {
		running = false;
		try {
			stream.close();
		} catch (IOException e) {
			// really cannot do much here
		}
		if(player != null) {
			try {
				if(player.getPlayState() != AudioTrack.PLAYSTATE_STOPPED)
					player.stop();
				player.release();
			} catch ( IllegalStateException e ) {
				//TODO Exception handling
			}
		}
	}
}