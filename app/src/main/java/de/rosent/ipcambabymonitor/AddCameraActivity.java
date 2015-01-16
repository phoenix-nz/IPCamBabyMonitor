package de.rosent.ipcambabymonitor;

import android.annotation.TargetApi;
//import android.content.Context;
//import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.app.Activity;
//import android.preference.PreferenceFragment;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import android.support.v4.app.NavUtils;

import de.rosent.ipcambabymonitor.cam.Camera;

//import java.util.List;

public class AddCameraActivity extends Activity {
	/**
	 * Determines whether to always show the simplified settings UI, where
	 * settings are presented in a single list. When false, settings are shown
	 * as a master/detail two-pane view on tablets. When true, a single pane is
	 * shown on tablets.
	 */
	//private static final boolean ALWAYS_SIMPLE_PREFS = false;
    private DataSource ds;
    private EditText cameraLabel;
    private EditText cameraHost;
    private EditText cameraPort;
    private EditText cameraUsername;
    private EditText cameraPassword;
    //private Context context;
	private Camera editCamera;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		int camId = 0;
		super.onCreate(savedInstanceState);
		setupActionBar();
        setContentView(R.layout.camerainfo);
		Bundle bundle = this.getIntent().getExtras();
		if (bundle != null) camId = bundle.getInt("cameraId");
		
        //context = this;
        ds = new DataSource(this);
        if( camId != 0 ) {
			editCamera = ds.select(camId);
			fillValues(editCamera);
        }
        cameraLabel = (EditText) findViewById(R.id.camera_info_label);
        cameraHost = (EditText) findViewById(R.id.camera_info_host);
        cameraPort = (EditText) findViewById(R.id.camera_info_port);
        cameraUsername = (EditText) findViewById(R.id.camera_info_username);
        cameraPassword = (EditText) findViewById(R.id.camera_info_password);
	}

	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			// Show the Up button in the action bar.
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_edit_camera, menu);
        return true;
    }
	
    public void fillValues(Camera cam) {
        cameraLabel = (EditText) findViewById(R.id.camera_info_label);
        cameraHost = (EditText) findViewById(R.id.camera_info_host);
        cameraPort = (EditText) findViewById(R.id.camera_info_port);
        cameraUsername = (EditText) findViewById(R.id.camera_info_username);
        cameraPassword = (EditText) findViewById(R.id.camera_info_password);
        
        cameraLabel.setText(cam.getLabel());
        cameraHost.setText(cam.getHost());
        cameraPort.setText(cam.getPort());
        cameraUsername.setText(cam.getUsername());
        cameraPassword.setText(cam.getPassword());
    }
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			NavUtils.navigateUpFromSameTask(this);
			return true;
        case R.id.menuItemSaveCamera:
        	cameraLabel = (EditText) findViewById(R.id.camera_info_label);
            cameraHost = (EditText) findViewById(R.id.camera_info_host);
            cameraPort = (EditText) findViewById(R.id.camera_info_port);
            cameraUsername = (EditText) findViewById(R.id.camera_info_username);
            cameraPassword = (EditText) findViewById(R.id.camera_info_password);
            String cameraLabelStr = cameraLabel.getText().toString();
            String cameraHostStr = cameraHost.getText().toString();
            if(!cameraHostStr.startsWith("http://") && !cameraHostStr.equals("")) {
                cameraHostStr = "http://" + cameraHostStr;
            }
            String cameraPortStr = cameraPort.getText().toString();
            String cameraUsernameStr = cameraUsername.getText().toString();
            String cameraPasswordStr = cameraPassword.getText().toString();
            if(!cameraLabelStr.equals("") && !cameraHostStr.equals("") && !cameraPortStr.equals("") && !cameraUsernameStr.equals("") && !cameraPasswordStr.equals("")) {
	            if( editCamera != null ) {
	            	ds.update(editCamera);
	        	} else {
                    // TODO - only Foscam Type hardcoded here
                    Camera c = Camera.createCamera(-1, cameraHostStr, cameraPortStr, cameraUsernameStr, cameraPasswordStr, cameraLabelStr, 1, getApplicationContext() );
                    int cameraId = ds.insert(c);
                    c.setId(cameraId);
	                finish();
	            } 
        	} else {
                Toast.makeText(this, R.string.warningEmptyFields, Toast.LENGTH_SHORT).show();
            }
            return true;
        case R.id.menuItemCancel:
            finish();
            return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Helper method to determine if the device has an extra-large screen. For
	 * example, 10" tablets are extra-large.
	 */
//	private static boolean isXLargeTablet(Context context) {
//		return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
//	}

	/**
	 * Determines whether the simplified settings UI should be shown. This is
	 * true if this is forced via {@link #ALWAYS_SIMPLE_PREFS}, or the device
	 * doesn't have newer APIs like {@link PreferenceFragment}, or the device
	 * doesn't have an extra-large screen. In these cases, a single-pane
	 * "simplified" settings UI should be shown.
	 */
//	private static boolean isSimplePreferences(Context context) {
//		return ALWAYS_SIMPLE_PREFS
//				|| Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
//				|| !isXLargeTablet(context);
//	}




}
