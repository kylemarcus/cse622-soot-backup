package edu.buffalo.cse.nsr.reptor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;

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
	private static final String DB_DIR = "/databases/";
	private static final String BACKUP_MANAGER = "com.example.backupmanager";
	
	private static HandlerThread handlerThread;
	private static IncomingHandler handler;
	private static Messenger mClientMessenger;
	private static MyServiceConnection myConnection;
	private static Context mCtx;
	
	public BackupLib(Context ctx, String packageName) {
		Log.d("DBG", "BACKUPLIB INITED");
		mCtx = ctx;
		myConnection = new MyServiceConnection(packageName);
		handlerThread = new HandlerThread("IPChandlerThread");
        handlerThread.start();
        handler = new IncomingHandler(handlerThread);
        mClientMessenger = new Messenger(handler);

        Intent intent = new Intent(BACKUP_MANAGER);
        
	    boolean ret = mCtx.bindService(intent, myConnection, 0);//Context.BIND_AUTO_CREATE);
	    
	    Log.d("DGB", "BindService returned " + ret);
	    
	    
	    //while (!myConnection.isBound) {
			try {
	    	Thread.sleep(100);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
	    //}
	    
	   // sharedPrefsRestore(packageName);
	    
	}
	
	public static void upload(String filename) {
		//TODO: Before calling this code, the file has to be copied into SD card
		Bundle bundle = new Bundle();
        bundle.putString("filename", filename);
    	sendMessage(bundle, BackupGlobals.REMOTE_WRITE); // 0 - READ, 1 - WRITE
	}
	
	public static int download(String filename) {
		Log.d("TEST", "File not found in SD card, downloading from dropbox");
		Bundle bundle = new Bundle();
        bundle.putString("filename", filename);
   		sendMessage(bundle, BackupGlobals.REMOTE_READ);
   		try {
   			Log.d("TEST", "Wating for read to complete");
			//synchronized (handler) {
			//	handler.wait();
			//}
   			synchronized (BackupGlobals.mutex) {
				BackupGlobals.mutex.wait();
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
	
	private static void sendMessage(Bundle bundle, int msg_type) {
		if (myConnection == null) {
			Log.d("ERROR", "myConnection is NULL");
		}
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
	
	public static void initBackup(String packageName) {
		
	}
	
	///////////////////////////////////////////////////////////////////////////
	//////////////////////////////// FILES ////////////////////////////////////
	///////////////////////////////////////////////////////////////////////////
	/*
	 * Copy file from backup dir to local dir
	 */
	public static void fileOpenRestore(String fileName, String packageName) {
		String fn = DATA_PATH + packageName + FILES_DIR + fileName;
		File f = new File(fn);
		if (!f.exists()) {
			int rc = download(fileName);
			if (rc == 0) {
				// file found, copy to storage
				try {
					String dlfile = getExternalFilesDir(packageName) + "/" + fileName;
					Process p = Runtime.getRuntime().exec("cp " + dlfile + " " + fn);
					p.waitFor();
				}
				catch (Exception e) {
					System.out.println("ERROR: Cound not download file: " + e.getMessage());
				}
			}
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
			upload(fileName);
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
		File folder = new File(extStorage + BACKUP_DIR + packageName);
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
			//String dest = getExternalStoragePath() + BACKUP_DIR + packageName + PREF_DIR;
			String backupCache = getExternalStoragePath() + "/backup_cache/";
			String metaName = packageName + "metadata.txt";
			String metaPath = backupCache + metaName;
			//copyDirectory(new File(source), new File(backupCache));
			File metadata = new File(metaPath);
			String[] children = new File(source).list();
			Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(metadata), "utf-8"));
			for (int i=0; i<children.length; i++) {
				 String newName = packageName + children[i];
				// File newFile = new File(newName);
				// File oldFile = new File(children[i]);
				// oldFile.renameTo(newFile);
				 copyDirectory(new File(source, children[i]),new File(backupCache, newName));
				 upload(newName);
				 writer.write(newName + '\n');
			}
			writer.close();
			upload(metaName);
		} catch (Exception e) {
			System.out.println("ERROR in saving prefs: " + e.getMessage());
		}
	}
	public static void sharedPrefsRestore(String packageName) {
		
	try {
			//String source = getExternalStoragePath() + BACKUP_DIR + packageName + PREF_DIR;
			String dest = DATA_PATH + packageName + PREF_DIR;
			String metaName = packageName + "metadata.txt";
			String backupCache = getExternalStoragePath() + "/backup_cache/";
			String metaPath = backupCache + metaName;
			int ret = download(metaName);
			if (ret != 0) {
				File metaData = new File(metaPath);
				BufferedReader br = new BufferedReader(new FileReader(metaData));
				String line;

				while((line = br.readLine())!= null){
					download(line);
					int len = packageName.length();
					String newName = line.substring(len);
					//File newFile = new File(newName);
					//File oldFile = new File(line);
					//oldFile.renameTo(newFile);
					copyDirectory(new File(backupCache, line), new File(dest, newName));
				}
				br.close();
			} else {
				Log.d("INSTRUMENT", "FILE NOT FOUND");
			}
		}
		catch (Exception e) {
			e.printStackTrace();
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