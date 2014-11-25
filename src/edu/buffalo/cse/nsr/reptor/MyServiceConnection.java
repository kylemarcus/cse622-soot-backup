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

	public boolean isBound() {
		return isBound;
	}
	@Override
	public void onServiceConnected(ComponentName className, 
                                            IBinder service) {
		
		Log.d("DBG", "Binding message");
		myService = new Messenger(service);
        isBound = true;
    }

    public void onServiceDisconnected(ComponentName className) {
        myService = null;
        isBound = false;
    }
}