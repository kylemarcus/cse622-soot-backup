package edu.buffalo.cse.nsr.reptor;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

class IncomingHandler extends Handler {
	private int errorCode = 0;
 
	public int getErrorCode() {
		return errorCode;
	}

	public IncomingHandler(HandlerThread thr) {
        super(thr.getLooper());
    }

    @Override
    public void handleMessage(Message msg) {
        switch (msg.what) {
        case BackupGlobals.REMOTE_READ_DONE:
            Log.d("TEST", "READ completed");
            errorCode = 0;
            //synchronized (this) {
            //	notify();
			//}
            synchronized (BackupGlobals.mutex) {
				BackupGlobals.mutex.notify();
			}
            break;
        case BackupGlobals.REMOTE_READ_FAILED:
            Log.d("TEST", "READ failed");
            errorCode = 404;
//            synchronized (this) {
//            	Log.d("TEST", "NOTIFYING FAILURE");
//            	notify();
//			}
            synchronized (BackupGlobals.mutex) {
            	Log.d("TEST", "NOTIFYING FAILURE");
				BackupGlobals.mutex.notify();
			}
            break;
        case BackupGlobals.REMOTE_WRITE_DONE:
            Log.d("TEST", "WRITE completed");
            break;
        default:
            break;
        }
    }
}