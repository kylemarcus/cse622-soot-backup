package edu.buffalo.cse.nsr.reptor;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Messenger;
import android.util.Log;

class MyServiceConnection implements ServiceConnection {
	Messenger myService;
	boolean isBound;
	public Messenger getMyService() {
		return myService;
	}
	String mPackageName;
	public MyServiceConnection(String packageName) {
		super();
		Log.d("DBG", "MyServiceConnection INITED");
		mPackageName = packageName;
	}
	
	public boolean isBound() {
		return isBound;
	}
	@Override
	public void onServiceConnected(ComponentName className, 
                                            IBinder service) {
		
		Log.d("DBG", "Binding message");
		myService = new Messenger(service);
        isBound = true;
        Thread.dumpStack();
        //BackupLib.sharedPrefsRestore(mPackageName);
	    
    }

    public void onServiceDisconnected(ComponentName className) {
        myService = null;
        isBound = false;
    }
}