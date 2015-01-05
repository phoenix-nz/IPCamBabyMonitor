package de.rosent.ipcambabymonitor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;

public class FoscamAudioSender extends Thread {
    private OutputStream stream;
    private AudioRecord recorder;
	private boolean running;
	private static final int AUDIO_BUFFER = 2560;
	private static final int AUDIO_PACKET_SIZE = 177;
	private int bufferSize = 0;
	
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
	
    public FoscamAudioSender( OutputStream stream ) {
    	super();
    	this.stream = stream;
//    	Buffer more than required - we seem to be relatively slow
    	bufferSize = AudioRecord.getMinBufferSize(8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    	if ( bufferSize < AUDIO_BUFFER ) bufferSize = AUDIO_BUFFER;
    	recorder = new AudioRecord( MediaRecorder.AudioSource.MIC, 8000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize );
    }
    
	public void run() {
		long timestamp = 0;
		long tickcount = 0;
		int count = 0; int audioPos;
		int predictor = 0;
    	int stepIndex = 0;
    	int step = 0;
    	byte val = 0;
    	int high = 0;
    	int low  = 0;
    	int delta = 0;
    	int diff = 0;
    	int audioIndex = 0;
    	short[] audio = new short[AUDIO_BUFFER];
        
		running = true;
		try {
			recorder.startRecording();
		} catch (IllegalStateException e) {
			stopAudio();
		}
		while(running) {
        	if (tickcount == 0 ) tickcount = SystemClock.uptimeMillis();
        	else tickcount +=4;
        	if (timestamp == 0) timestamp = System.currentTimeMillis() / 1000;
    		recorder.read(audio, 0, AUDIO_BUFFER);
            // update packet count
        	count++;
            
            if(audio != null) {
            	byte[] frameData = new byte[FoscamUtil.HeaderSize + AUDIO_PACKET_SIZE];
            	for (int i = 0; i < 4; i++)                 //preamble
            		frameData[i] = FoscamUtil.MO_V[i];
            	frameData[15] = frameData[19] = (byte) 0xb1;
            	frameData[FoscamUtil.CommandPos] = 3;
            	// We have to create all of the preamble
            	// First four bytes = timestamp
            	audioPos = FoscamUtil.HeaderSize;
            	byte[] timestampBytes = ByteBuffer.allocate(8).putLong(tickcount).array();
            	for (int i = 0; i < 4; i++)
            		frameData[audioPos+3-i] = timestampBytes[i+4];
            	audioPos += 4;
            	// Next four bytes = sequence number
            	byte[] countBytes = ByteBuffer.allocate(4).putInt(count).array();
            	for (int i = 0; i < 4; i++)
            		frameData[audioPos+3-i] = countBytes[i];
            	// The the rest of the timestamp - this could be useful later...
            	audioPos +=4;
            	timestampBytes = ByteBuffer.allocate(8).putLong(timestamp).array();
            	for (int i = 0; i < 4; i++)
            		frameData[audioPos+3-i] = timestampBytes[i+4];
            	audioPos +=4;
            	// Make sure we are using ADPCM
            	// Just leave everything on 0
            	audioPos +=1;
            	// The next four bytes are always 160 - length of audio
            	frameData[audioPos] = (byte) 0xa0;
            	
            	audioPos += 4;
            	audioIndex = 0;
            	for(int i = audioPos; i < frameData.length; i++) {            		
            		// runthrough for high;
            		step = ima_step_table[stepIndex];
            		diff = audio[audioIndex] - predictor;
            		audioIndex++;
            		if(diff >= 0)
            			high = 0;
            		else {
            			high = 8;
            			diff = -diff;
            		}

            		delta = step;
            		if( diff >= delta ) {
            			high |= 4;
            			diff -= delta;
            		}
            		delta >>= 1;
            		if( diff >= delta ) {
            			high |= 2;
            			diff -= delta;
            		}
            		delta >>= 1;
            		if( diff >= delta )
            			high |= 1;

            		delta = step >> 3;
            		if((high & 4) != 0)	delta += step;
            		if((high & 2) != 0) delta += (step >> 1);
            		if((high & 1) != 0) delta += (step >> 2);
            		if((high & 8) != 0) predictor -= delta;
            		else				predictor += delta;
            		
            		if (predictor > Short.MAX_VALUE) predictor = Short.MAX_VALUE;
            		else if (predictor < Short.MIN_VALUE) predictor = Short.MIN_VALUE;

            		stepIndex += ima_index_table[high];
            		if (stepIndex > 88) stepIndex = 88;
            		else if (stepIndex < 0) stepIndex = 0;
            		
            		// runthrough for low;
            		step = ima_step_table[stepIndex];
            		diff = audio[audioIndex] - predictor;
            		audioIndex++;
            		if(diff >= 0)
            			low = 0;
            		else {
            			low = 8;
            			diff = -diff;
            		}

            		delta = step;
            		if( diff >= delta ) {
            			low |= 4;
            			diff -= delta;
            		}
            		delta >>= 1;
            		if( diff >= delta ) {
            			low |= 2;
            			diff -= delta;
            		}
            		delta >>= 1;
            		if( diff >= delta )
            			low |= 1;

            		delta = step >> 3;
            		if((low & 4) != 0)	delta += step;
            		if((low & 2) != 0) 	delta += (step >> 1);
            		if((low & 1) != 0) 	delta += (step >> 2);
            		if((low & 8) != 0) 	predictor -= delta;
            		else				predictor += delta;
            		
            		if (predictor > Short.MAX_VALUE) predictor = Short.MAX_VALUE;
            		else if (predictor < Short.MIN_VALUE) predictor = Short.MIN_VALUE;

            		stepIndex += ima_index_table[low];
            		if (stepIndex > 88) stepIndex = 88;
            		else if (stepIndex < 0) stepIndex = 0;
            		
            		// finally move to frameData:
            		high <<= 4 & 0xf0;
            		val = (byte) ((byte) ( high | low ) & 0xff);
            		frameData[i] = val;
            	}
            	try {
					stream.write(frameData);
					stream.flush();
				} catch (IOException e) {
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
		if(recorder != null) {
			try {
				if(recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING)
					recorder.stop();
			} catch (IllegalStateException e) {
				//do nothing
			}
			recorder.release();
		}
	}
}