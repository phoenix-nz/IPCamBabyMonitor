package de.rosent.ipcambabymonitor;

import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

public class MonitorActivity extends Activity {
	private DataSource ds;
    private Camera activeCam;
	private ImageView iStream;
	private Handler messageHandler;
	private View pBar;
    private GestureDetector gestureDetector;
    private View.OnTouchListener gestureListener;
    private ImageButton switchAudioBtn = null;
    private ImageButton switchIRBtn = null;
    private ImageButton switchMicBtn = null;
    private Thread switchVideoThrd = null;
    private Thread switchAudioThrd = null;
    private Thread switchIRThrd = null;
    private Thread switchMicThrd = null;
    private Intent alarmConnection = null;
    
    private boolean checkAlarm;
    private boolean keepAudioRunning;
    private Context context;
    private boolean irOn;
    
    public String currDebugMess = null;
	
    @Override
    public Object onRetainNonConfigurationInstance() {
    	Camera c = activeCam.clone();
    	// we cannot keep video going on a screen rotation
    	// thus we stop it here
    	c.stopVideo();
    	return c;
    }
    
	@SuppressWarnings("deprecation")
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        
        gestureDetector = new GestureDetector(this, new MyGestureDetector());
        gestureListener = new View.OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                return gestureDetector.onTouchEvent(event);
            }
        };
        
        ds = new DataSource(this);
        context = this;
        messageHandler = new Handler();
        
        activeCam = (Camera) getLastNonConfigurationInstance();
    }

	
	public void onResume() {
		super.onResume();
		
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
		checkAlarm = sharedPref.getBoolean(SettingsActivity.KEY_PREF_ALARM_CHECK, false);
		keepAudioRunning = sharedPref.getBoolean(SettingsActivity.KEY_PREF_BACKGROUND_AUDIO, false);
		boolean flipVert = sharedPref.getBoolean(SettingsActivity.KEY_PREF_FLIP_VERTICALLY, false);
		boolean flipHoriz = sharedPref.getBoolean(SettingsActivity.KEY_PREF_FLIP_HORIZONTALLY, false);
		
		if (activeCam == null)
			activeCam = ds.getSelectedCamera();
        if( activeCam == null ) {
        	//no active camera exists...
            Toast.makeText(this, R.string.warningEmptyFields, Toast.LENGTH_SHORT).show();
        } else {
        	// Make sure screen doesn't turn off
        	getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        	
        	activeCam.setContext(context);
        	// Start video
        	if(activeCam.isStreamingVideo());
        		activeCam.stopVideo();
        	switchVideo();
        	
        	// Check Audio
        	switchAudioBtn = (ImageButton)findViewById(R.id.toggleSound);
        	if (!activeCam.isStreamingAudio()) {
        		switchAudioBtn.setImageResource(R.drawable.device_access_volume_muted);
        	} else {
        		switchAudioBtn.setImageResource(R.drawable.device_access_volume_on);
        	}
//        	switchAudioBtn.setChecked(activeCam.isStreamingAudio());

        	// Check Mic
        	switchMicBtn = (ImageButton)findViewById(R.id.toggleMic);
        	if (!activeCam.isStreamingMic()) {
        		switchMicBtn.setImageResource(R.drawable.device_access_mic_muted);
        	} else {
        		switchMicBtn.setImageResource(R.drawable.device_access_mic);
        	}
//        	switchMicBtn.setChecked(activeCam.isStreamingMic());
        	
        	// Check IR -- TODO is this possible?
//        	switchIRBtn = (ImageButton)findViewById(R.id.toggleIR);
//        	if (!activeCam.getIR()) {
//        		switchIRBtn.setImageResource(R.drawable.device_access_bightness_low);
//        	} else {
//        		switchIRBtn.setImageResource(R.drawable.device_access_brightness_high);
//        	}
        	
        	// Set Image Orientation
        	activeCam.flip( flipVert, flipHoriz );
        	
        	// Watch Alarm
        	setCheckAlarm(checkAlarm);
        }
	}

	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_monitor, menu);
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.menu_settings:
				startActivity(new Intent(this, SettingsActivity.class));
				return true;
			case R.id.choose_camera:
				if( activeCam != null) {
					activeCam.stop(false);
					activeCam = null;
				}
				startActivity(new Intent(this, SelectCameraActivity.class));
				return true;
		}
		
    	return super.onOptionsItemSelected(item);
    }
    
    public void onDestroy() {
        super.onDestroy();
        if ( activeCam != null ) {
        	activeCam.stop(false);
        }
    }
    
    public void onStop() {
    	super.onStop();
        if ( activeCam != null ) {
        	activeCam.stop(keepAudioRunning);
        }
    }
    
    public void onPause() {
    	super.onPause();
        if ( activeCam != null ) {
        	activeCam.stop(keepAudioRunning);
        }
    }
    
    public void switchVideo( ) {
    	iStream = (ImageView)findViewById(R.id.imagestream);
    	iStream.setOnTouchListener(gestureListener);
    	pBar = findViewById(R.id.loadImage);
    	
    	if( switchVideoThrd == null || !switchVideoThrd.isAlive() ) {
    		switchVideoThrd = new Thread(videoSwitcher);
    		switchVideoThrd.start();
    	}
    	
    }
    
    final Runnable videoSwitcher = new Runnable() {
    	public void run( ) {
    		//TODO translatable...
    		debugMessage( "Connecting.." );
    		if (activeCam.streamVideo(iStream, messageHandler)) {
//    				iStream.post(new Runnable() {
//    	        		public void run() {
//    	        			iStream.setVisibility(View.VISIBLE);
//    	        		}
//    	        	});
//    				pBar.post(new Runnable() {
//    	        		public void run() {
//    	        			pBar.setVisibility(View.GONE);
//    	        		}
//    	        	});
    			return;
    		}
    		// If we get to here we couldn't connect...

			//TODO translatable...
			debugMessage( "Could not connect");
    	}
    };
    
    public void switchAudio( View view ) {
    	//make sure Button and Cam agree on status...
    	//should really do this at startup
    	this.switchAudioBtn = (ImageButton) view;
    	switchAudioBtn.setEnabled(false);
    	if( switchAudioThrd == null || !switchAudioThrd.isAlive() ) {
    		switchAudioThrd = new Thread(audioSwitcher);
    		switchAudioThrd.start();
    	}
    	
    }
    
    final Runnable audioSwitcher = new Runnable() {
    	public void run( ) {
    		if ( switchAudioBtn != null ) {
    			if (!activeCam.isStreamingAudio()) {
    	    		activeCam.streamAudio( );
    	    	} else { //if (activeCam.isStreamingAudio()) {
    	    		activeCam.stopAudio();
    	    	}
    			// check if it worked:
    			if(!activeCam.isStreamingAudio()) {
    				switchAudioBtn.post(new Runnable() {
    					public void run() {
    						switchAudioBtn.setImageResource(R.drawable.device_access_volume_muted);
    						switchAudioBtn.setEnabled(true);
    					}
    		    }); 
    		    } else {
	        		switchAudioBtn.post(new Runnable() {
	        			public void run() {
		        			switchAudioBtn.setImageResource(R.drawable.device_access_volume_on);
			        		switchAudioBtn.setEnabled(true);
	        			}
	        		});
    		    }
    		}
    	}
    };
    
    public void switchMic( View view ) {
    	//make sure Button and Cam agree on status...
    	//should really do this at startup
    	this.switchMicBtn = (ImageButton) view;
    	switchMicBtn.setEnabled(false);
    	if( switchMicThrd == null || !switchMicThrd.isAlive() ) {
    		switchMicThrd = new Thread(micSwitcher);
    		switchMicThrd.start();
    	}
    	
    }
    
    final Runnable micSwitcher = new Runnable() {
    	public void run( ) {
    		if ( switchMicBtn != null ) {
    			if (!activeCam.isStreamingMic()) {
    	    		activeCam.streamMic( );
    	    	} else if (activeCam.isStreamingMic()) {
    	    		activeCam.stopMic();
    	    	}
    			// check if it worked:
    			if(!activeCam.isStreamingMic()) {
    				switchMicBtn.post(new Runnable() {
    					public void run() {
    						switchMicBtn.setImageResource(R.drawable.device_access_mic_muted);
    						switchMicBtn.setEnabled(true);
    					}
    		    }); 
    		    } else {
	        		switchMicBtn.post(new Runnable() {
	        			public void run() {
		        			switchMicBtn.setImageResource(R.drawable.device_access_mic);
			        		switchMicBtn.setEnabled(true);
	        			}
	        		});
    		    }
    		}
    	}
    };
    
    public void switchIR( View view ) {
    	if( this.switchIRBtn == null )
    		switchIRBtn = (ImageButton) view;
    	switchIRBtn.setEnabled(false);
    	if(switchIRThrd == null || !switchIRThrd.isAlive()) {
    		switchIRThrd = new Thread(irSwitcher);
    		switchIRThrd.start();
    	}
    }
    
    final Runnable irSwitcher = new Runnable() {
    	public void run( ) {
    		if ( switchIRBtn != null ) {
	    		activeCam.setIR( irOn );
	    		if( !irOn ) {
		    		switchIRBtn.post(new Runnable() {
		    			public void run() {
		    				switchIRBtn.setImageResource(R.drawable.device_access_bightness_low);
			    			switchIRBtn.setEnabled(true);
		    			}
		    		});
	    		} else {
	    			switchIRBtn.post(new Runnable() {
		    			public void run() {
		    				switchIRBtn.setImageResource(R.drawable.device_access_brightness_high);
			    			switchIRBtn.setEnabled(true);
		    			}
		    		});		
		    	}
	    		irOn = !irOn;
    		}
    	}
    };
    
    public void setCheckAlarm( boolean check ) {
    	checkAlarm = check;
    	if(!check && alarmConnection != null) {
    		context.stopService(alarmConnection);
    	}
    	else if(check) {
    		alarmConnection = new Intent(this, AlarmChecker.class);
    		context.startService(alarmConnection);
    	}
    }
    
    public void debugMessage( String message ) {
    	currDebugMess = message;
    	if (!pBar.post(new Runnable() {
    		public void run() {
    			if (currDebugMess != null) {
    				//TODO Translatable
    				Toast toast = Toast.makeText(context, currDebugMess, Toast.LENGTH_LONG);
    				toast.show();
    			}
    		}
    	}) ) System.err.println( "Could not post: <<<<<<<<<<<<<<<<<<<<<<<<" );
    	System.err.println( message + "<<<<<<<<<<<<<<<<<<<<<<" );
    }
    

	class MyGestureDetector extends SimpleOnGestureListener {
    	/* Primitive Method that simply tells the activeCamera to start
    	 * moving as long as the pointer is active!
    	 */
        @Override
        public boolean onDown(MotionEvent e) {
        	debugMessage( "Position:" + e.getX() + ":" + e.getY() );
        	if (activeCam == null)
        		return false;
        	else if (!activeCam.isStreamingVideo()){
        		iStream.post( new Runnable() {
        			public void run() {
        				switchVideo();
        			}
        		});
        		return true;
        	} else {
        		activeCam.move(e);
        		return true;
        	}
        }
    }
}
