package de.rosent.ipcambabymonitor;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.ArrayList;

/*
 * All of the database management happens in this class.
 */

public class DataSource extends SQLiteOpenHelper {

    public static final String databaseName = "rosentBabyMon";
    public static final String preferencesKey = "de.rosent.foscamBabyMonitor";
    public static final int databaseVersion = 1;
    public int selectedCamera = 0;
    public Context context;
    
    public DataSource(Context context) {
        super(context, databaseName, null, databaseVersion);
    	this.context = context;
    	initSelectedCamera();
    }
    
    @Override
    public void onCreate(SQLiteDatabase db) {
        String createCameraTable = "create table tblCamera (_id integer primary key autoincrement, host text, port text, username text, password text, camera_desc text, camera_type_id integer)";
        db.execSQL(createCameraTable);
    }
    
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { }
    
    public int insert(Camera c) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        int cameraId = 0;
        values.put("host", c.getHost());
        values.put("port", c.getPort());
        values.put("username", c.getUsername());
        values.put("password", c.getPassword());
        values.put("camera_desc", c.getLabel());
        values.put("camera_type_id", c.getTypeId());
        cameraId = (int)db.insert("tblCamera", null, values);
        if (cameraId == 1) { //First camera to be inserted...
        	setSelectedCamera(cameraId);
        }
        db.close();
        return cameraId;
    }
    
    public void setSelectedCamera(int cameraId){
    	this.selectedCamera = cameraId;
    	SharedPreferences.Editor editor = this.context.getSharedPreferences( preferencesKey, Context.MODE_PRIVATE).edit();
    	editor.putInt("selectedCamera", cameraId);
    	editor.commit();
    }
    
    public void initSelectedCamera() {
    	SharedPreferences prefs = this.context.getSharedPreferences(preferencesKey, Context.MODE_PRIVATE);
    	this.selectedCamera = prefs.getInt("selectedCamera", this.selectedCamera);
    }
    
    public void update(Camera c) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("host", c.getHost());
        values.put("port", c.getPort());
        values.put("username", c.getUsername());
        values.put("password", c.getPassword());
        values.put("camera_desc", c.getLabel());
        values.put("camera_type_id", c.getTypeId());
        db.update("tblCamera", values, "_id=?", new String[] { Integer.toString(c.getId()) });
        db.close();
        if (c.getSelected()) {
        	setSelectedCamera(c.getId());
        }
    }
    
    public Camera getSelectedCamera() {
		Camera c = null;
    	if (selectedCamera != 0) {
    		SQLiteDatabase db = this.getReadableDatabase();
            Cursor cursor = db.query("tblCamera", new String[] { "_id", "host", "port", "username", "password", "camera_desc", "camera_type_id" }, "_id=?", new String[] { String.valueOf(selectedCamera) }, null, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
                c = new Camera(Integer.parseInt(cursor.getString(0)), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), Integer.parseInt(cursor.getString(6)));
                c.setSelected(true);
            }
            cursor.close();
            db.close();
    	}
        return c;
    }
    
    public Camera select(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query("tblCamera", new String[] { "_id", "host", "port", "username", "password", "camera_desc", "camera_type_id" }, "_id=?", new String[] { String.valueOf(id) }, null, null, null, null);
        if (cursor != null)
            cursor.moveToFirst();
        Camera c = new Camera(Integer.parseInt(cursor.getString(0)), cursor.getString(1), cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5), Integer.parseInt(cursor.getString(6)));
        cursor.close();
        db.close();
        c.setSelected(true);
        setSelectedCamera(c.getId());
        return c;
    }
    
    public ArrayList<Camera> selectAll() {
        ArrayList<Camera> cameraList = new ArrayList<Camera>();
        String selectQuery = "select * from tblCamera";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);
        if (cursor.moveToFirst()) {
            do {
                Camera c = new Camera();
                c.setId(Integer.parseInt(cursor.getString(0)));
                c.setHost(cursor.getString(1));
                c.setPort(cursor.getString(2));
                c.setUsername(cursor.getString(3));
                c.setPassword(cursor.getString(4));
                c.setLabel(cursor.getString(5));
                c.setTypeId(Integer.parseInt(cursor.getString(6)));
                if (c.getId() == selectedCamera){
                	c.setSelected(true);
                }	else {
                	c.setSelected(false);
                }
                cameraList.add(c);
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return cameraList;
    }
    
    public void delete(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete("tblCamera", "_id=?", new String[] { Integer.toString(id) });
        db.close();
        if (id == selectedCamera) {
        	setSelectedCamera(0);
        }
    }

}
