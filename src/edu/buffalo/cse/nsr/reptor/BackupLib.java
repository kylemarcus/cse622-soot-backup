package edu.buffalo.cse.nsr.reptor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

public class BackupLib {
	
	private static final String DATA_PATH = "/data/data/";
	private static final String FILES_DIR = "/files/";
	private static final String PREF_DIR = "/shared_prefs/";
	private static final String BACKUP_DIR = "/cse622_backup/";
	private static final String DB_DIR = "/database/";
	private static final String BACKUP_MANAGER = "com.example.backupmanager";
	
	private HandlerThread handlerThread;
	private IncomingHandler handler;
	private Messenger mClientMessenger;
	private MyServiceConnection myConnection;
	private Context mCtx;
	
	public BackupLib(Context ctx) {
		mCtx = ctx;
		myConnection = new MyServiceConnection();
		handlerThread = new HandlerThread("IPChandlerThread");
        handlerThread.start();
        handler = new IncomingHandler(handlerThread);
        mClientMessenger = new Messenger(handler);

        Intent intent = new Intent(BACKUP_MANAGER);
        
	    mCtx.bindService(intent, myConnection , Context.BIND_AUTO_CREATE);
	}
	
	public void upload(String filename) {
		//TODO: Before calling this code, the file has to be copied into SD card
		Bundle bundle = new Bundle();
        bundle.putString("filename", filename);
    	sendMessage(bundle, BackupGlobals.REMOTE_WRITE); // 0 - READ, 1 - WRITE
	}
	
	public int download(String filename) {
		Log.d("TEST", "File not found in SD card, downloading from dropbox");
		Bundle bundle = new Bundle();
        bundle.putString("filename", filename);
   		sendMessage(bundle, BackupGlobals.REMOTE_READ);
   		try {
   			Log.d("TEST", "Wating for read to complete");
			synchronized (handler) {
				handler.wait();
			}

			Log.d("TEST", "read completed");
			if (handler.getErrorCode() != 0) {
				Log.d("TEST", "File not found");
				return 1;
			}
			
   		} catch (InterruptedException e) {
			e.printStackTrace();
		} 
   		Log.d("TEST", "downloaded file from dropbox");
   		//TODO: The file has to be copied from SD card cache back to the original location
   		return 0;
   }
	
	private void sendMessage(Bundle bundle, int msg_type) {
		if (!myConnection.isBound()) {
			Log.d("ERROR", "STILL NOT INIT'ed");
			return;
		}
		Message msg = Message.obtain();
		
		msg.setData(bundle);
		msg.what = msg_type;
		msg.replyTo = mClientMessenger;

		try {
			myConnection.getMyService().send(msg);
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	///////////////////////////////////////////////////////////////////////////
	//////////////////////////////// INIT /////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////
	
	public static void initBackup() {
		
	}
	
	///////////////////////////////////////////////////////////////////////////
	//////////////////////////////// FILES ////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////
	/*
	 * Copy file from backup dir to local dir
	 */
	public static void fileOpenRestore(String fileName, String packageName) {
		File f = new File(DATA_PATH + packageName + FILES_DIR + fileName);
		if (!f.exists()) {
			// file does not exist so ask backup service for it using a intent?
			// call backup service, send it fileName and packageName
			// copy file from ext storage to local storage
		}
	}
	/*
	 * Copy file to backup directory on external storage directory
	 */
	public static void fileWriteBackup(FileOutputStream fos, String fileName, String packageName) {
		//TODO: look for directory structure inside filename and create those directories too
		try {
			fos.flush();
			String fromFile = DATA_PATH + packageName + FILES_DIR + fileName;
			String toFile = getExternalFilesDir(packageName) + "/" + fileName;
			Process p = Runtime.getRuntime().exec("cp " + fromFile + " " + toFile);
			p.waitFor();
			if (p.exitValue() != 0) {
				throw new Exception("ERROR: Copy failed from file " + fromFile + " to " + toFile);
			}
		} catch (Exception e) {
			System.out.println("ERROR: Cound not complete file write backup: " + e.getMessage());
		}
	}
	/*
	 * Returns the path where to store files in sd card using the package name
	 */
	private static String getExternalFilesDir(String packageName) throws Exception {
		String extStorage = getExternalStoragePath();
		// We can read and write the media
		File folder = new File(extStorage + BACKUP_DIR + packageName + FILES_DIR);
		if (!folder.exists()) {
			if (folder.mkdirs()) {
				return folder.getAbsolutePath();
			} else {
				throw new Exception("Could not create dir: " + folder.getAbsolutePath());
			}
		} else {
			return folder.getAbsolutePath();
		}
	}
	private static String getExternalStoragePath() throws Exception {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return Environment.getExternalStorageDirectory().toString();
		} else {
			throw new Exception("Can not write to or SD card is not mounted");
		}
	}
	///////////////////////////////////////////////////////////////////////////
	//////////////////////////// SHARED PREFS /////////////////////////////////
	///////////////////////////////////////////////////////////////////////////
	public static void sharedPrefsBackup(String packageName) {
		try {
			String source = DATA_PATH + packageName + PREF_DIR;
			String dest = getExternalStoragePath() + BACKUP_DIR + packageName + PREF_DIR;
			copyDirectory(new File(source), new File(dest));
		} catch (Exception e) {
			System.out.println("ERROR in saving prefs: " + e.getMessage());
		}
	}
	public static void sharedPrefsRestore(String packageName) {
		
	try {
			String source = getExternalStoragePath() + BACKUP_DIR + packageName + PREF_DIR;
			String dest = DATA_PATH + packageName + PREF_DIR;
			File folder = new File(source);
			if (folder.exists()){
			copyDirectory(new File(source), new File(dest));
			}
	}
		catch (Exception e) {
			System.out.println("ERROR in replacing prefs: " + e.getMessage());
		}	
	}
	private static void copyDirectory(File sourceLocation , File targetLocation) throws IOException {
		if (sourceLocation.isDirectory()) {
			if (!targetLocation.exists() && !targetLocation.mkdirs()) {
				throw new IOException("Cannot create dir: " + targetLocation.getAbsolutePath());
			}
			String[] children = sourceLocation.list();
			for (int i=0; i<children.length; i++) {
				copyDirectory(new File(sourceLocation, children[i]),
						new File(targetLocation, children[i]));
			}
		} else {
			// make sure the directory we plan to store the recording in exists
			File directory = targetLocation.getParentFile();
			if (directory != null && !directory.exists() && !directory.mkdirs()) {
				throw new IOException("Cannot create dir: " + directory.getAbsolutePath());
			}
			InputStream in = new FileInputStream(sourceLocation);
			OutputStream out = new FileOutputStream(targetLocation);
			// Copy the bits from instream to outstream
			byte[] buf = new byte[1024];
			int len;
			while ((len = in.read(buf)) > 0) {
				out.write(buf, 0, len);
			}
			in.close();
			out.close();
		}
	}
	///////////////////////////////////////////////////////////////////////////
	//////////////////////////// DATABASES /////////////////////////////////
	///////////////////////////////////////////////////////////////////////////
	public static void databaseBackup(String packageName) {
		try {
			String source = DATA_PATH + packageName + DB_DIR;
			String dest = getExternalStoragePath() + BACKUP_DIR + packageName + DB_DIR;
			copyDirectory(new File(source), new File(dest));
		} catch (Exception e) {
			System.out.println("ERROR in saving DB: " + e.getMessage());
		}
	}
	public static void databaseRestore(String packageName) {
		try {
			String source = getExternalStoragePath() + BACKUP_DIR + packageName + DB_DIR;
			String dest = DATA_PATH + packageName + DB_DIR;
			File folder = new File(source);
			if (folder.exists()){
			copyDirectory(new File(source), new File(dest));
			}
	}
		catch (Exception e) {
			System.out.println("ERROR in replacing DB: " + e.getMessage());
		}	
		
	}
}