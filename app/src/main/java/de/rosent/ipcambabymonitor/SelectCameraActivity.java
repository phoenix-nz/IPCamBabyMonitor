package de.rosent.ipcambabymonitor;

import java.util.ArrayList;

import android.os.Build;
import android.os.Bundle;
//import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.support.v4.app.NavUtils;

public class SelectCameraActivity extends Activity {
    
	ListView lvCamera;
    ArrayList<Camera> cameraList;
    CameraListAdapter adapter;
    DataSource ds;
    Context context;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_select_camera);
		setupActionBar();
		
        context = this;
        cameraList = new ArrayList<Camera>(); 
        
//        if(oldDBExists()) {
//            System.out.println("FOUND OBSOLETE DATABASE");
//            renameOldDB();
//        }
        
        ds = new DataSource(this);      
        
//        //Upgrade db from 1.x.x to 2.0.0
//        upgradeDB();
        
        lvCamera = (ListView) findViewById(R.id.listCameras);
        adapter = new CameraListAdapter(this, cameraList);
        lvCamera.setAdapter(adapter);
        lvCamera.setOnItemLongClickListener(cameraLongClick);
        lvCamera.setOnItemClickListener(cameraClick);
        for(int i = 0; i < ds.selectAll().size(); i++) {
            cameraList.add(ds.selectAll().get(i));
//            if(ds.selectedCamera == ds.selectAll().get(i).getId())
//            TODO highlight selected cameras
       
        }
		
	}

	public void onResume() {
		super.onResume();
        cameraList.clear();
        for(int i = 0; i < ds.selectAll().size(); i++) {
            cameraList.add(ds.selectAll().get(i));
        }
        adapter.notifyDataSetChanged();
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
		getMenuInflater().inflate(R.menu.activity_select_camera, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			NavUtils.navigateUpFromSameTask(this);
			return true;
		case R.id.menuAddCamera:
			startAddCamera(this.getCurrentFocus());
			return true;
		}
		
		return super.onOptionsItemSelected(item);
	}

	public void startAddCamera(View view) {
		startActivity(new Intent(this, AddCameraActivity.class));
	}

    
    private OnItemClickListener cameraClick = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> a, View v, int position, long id) {
            Object o = lvCamera.getItemAtPosition(position);
            Camera fullObject = (Camera)o;
            ds.setSelectedCamera(fullObject.getId());
//            cameraList.clear();
//            for(int i = 0; i < ds.selectAll().size(); i++) {
//                cameraList.add(ds.selectAll().get(i));
//            }
            adapter.notifyDataSetChanged();
            NavUtils.navigateUpFromSameTask((Activity) context);
        }
    };
    
    private OnItemLongClickListener cameraLongClick = new OnItemLongClickListener() {
        public boolean onItemLongClick(AdapterView<?> a, View v, int position, long id) {
            Object o = lvCamera.getItemAtPosition(position);
            final Camera fullObject = (Camera)o;
            final Resources res = getResources();
            final String[] items = {res.getString(R.string.edit), res.getString(R.string.delete)};
            AlertDialog.Builder builder = new AlertDialog.Builder(SelectCameraActivity.this);
            builder.setTitle(R.string.choose_action);
            builder.setItems(items, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int item) {
                    if(item == 0) {
                        Intent updateCamera = new Intent(context, AddCameraActivity.class);
                        updateCamera.putExtra("cameraId", fullObject.getId());
                        startActivity(updateCamera);
                    }
                    if(item == 1) {
                        ds.delete(fullObject.getId());
                        cameraList.clear();
                        for(int i = 0; i < ds.selectAll().size(); i++) {
                            cameraList.add(ds.selectAll().get(i));
                        }
                        adapter.notifyDataSetChanged();
                    }
                }
            });
            builder.show();
            return true;
        }
    };
}
