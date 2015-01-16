package de.rosent.ipcambabymonitor;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import de.rosent.ipcambabymonitor.cam.Camera;
import de.rosent.ipcambabymonitor.cam.foscam.FoscamUtil;
import de.rosent.ipcambabymonitor.cam.Alarm;

public class AlarmChecker extends Service {
	private NotificationManager nManager;
    private PowerManager		 pManager;
    private PowerManager.WakeLock backgroundLock = null;
	private int NOTIFICATION = 1;
    private final IBinder mBinder = new LocalBinder();
    private Camera activeCam;
    private Thread checkAlarmThrd = null;
    private boolean checkAlarm = false;
    private Context context;
    private boolean alarmVibrate;
    private String   alarmSound;
    
    
    public class LocalBinder extends Binder {
        AlarmChecker getService() {
            return AlarmChecker.this;
        }
    }
    
    @Override
    public void onCreate() {
        nManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        pManager = (PowerManager)getSystemService(Context.POWER_SERVICE);
    }
    
    @SuppressWarnings("deprecation")
	private Notification createAlarmNotif( Alarm alarm ) {
    	PendingIntent pIntent = PendingIntent.getActivity(context, 0, new Intent(context, MonitorActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notif;
        if(alarm != null) {
            notif = new Notification(R.drawable.notif_active, alarm.getText(), System.currentTimeMillis());
            notif.setLatestEventInfo(context, alarm.getText(), getText(R.string.notifyAlarm), pIntent);
            if (alarmVibrate )
                notif.vibrate = new long[] {0,500,50,500,50,500,50,500,50,500,50,500,50,1000};
            if (alarmSound != null)
                notif.sound = Uri.parse(alarmSound);
        } else {
            notif = new Notification(R.drawable.notif_passive, getText(R.string.notifyWatch), System.currentTimeMillis());
            notif.setLatestEventInfo(context, getText(R.string.notifyWatch), getText(R.string.notifyNoAlarm), pIntent);
        }
		return notif;
    }
    
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		this.context = this;
		if(activeCam == null) {
			DataSource ds = new DataSource(this);
			SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
			alarmVibrate = sharedPref.getBoolean(SettingsActivity.KEY_PREF_ALARM_VIBRATE, true);
			alarmSound = sharedPref.getString(SettingsActivity.KEY_PREF_ALARM_SOUND, "");
			activeCam = ds.getSelectedCamera();
			if(activeCam != null) {
				checkAlarm = true;
				startForeground(startId, createAlarmNotif(null));
	    		backgroundLock = pManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "FoscamBabyMonLock");
				checkAlarmThrd = new Thread(iAlarmChecker);
	    		checkAlarmThrd.start();
			}
		}
		return Service.START_STICKY;
	}
	

    @Override
    public void onDestroy() {
        // Cancel the persistent notification.
        nManager.cancel(NOTIFICATION);
        checkAlarm = false;
        stopForeground(true);
        if(backgroundLock != null && backgroundLock.isHeld())
        	backgroundLock.release();

        // Tell the user we stopped.
//        Toast.makeText(this, R.string.local_service_stopped, Toast.LENGTH_SHORT).show();
    }
	
	@Override
	public IBinder onBind(Intent arg0) {
		return mBinder;
	}
	
	final Runnable iAlarmChecker = new Runnable() {
		Notification notif = null;
		boolean alarm = false;
	
		public void run( ) {
			backgroundLock.acquire();
			while( activeCam != null && checkAlarm) {
				if(activeCam.isAlarm() ) {
					if(!alarm) {
	    				if(notif != null)
	    					nManager.cancel(NOTIFICATION);
	    				notif = createAlarmNotif(activeCam.getAlarm());
	    				nManager.notify(NOTIFICATION, notif);
					}
					alarm = true;
					//switchAudio(switchAudioBtn);
				} else {
					if(alarm && nManager != null && notif != null) {
						nManager.cancel(NOTIFICATION);
						notif = createAlarmNotif(null);
	    				nManager.notify(NOTIFICATION, notif);
					}
					alarm = false;
				}
				try {
					Thread.sleep(2550);
				} catch (InterruptedException e) {
					checkAlarm = false;
				}
			}
			backgroundLock.release();
	}
};

}
