package edu.buffalo.cse.nsr.reptor;

import java.io.File;
import java.io.FileOutputStream;

import android.os.Environment;

public class BackupLib {
	
	public static final String DATA_PATH = "/data/data/";
	public static final String FILES_DIR = "/files/";
	public static final String BACKUP_DIR = "/cse622_backup/";
	
	/*
	 * Copy file to backup directoy on external storage directory
	 */
	public static void fileWriteBackup(FileOutputStream fos, String fileName, String packageName) {
		
		try {
			
			fos.flush();
			
			String fromFile = DATA_PATH + packageName + FILES_DIR + fileName;
			String toFile = getExternalFilesDir(packageName) + "/" + fileName;
			Process p = Runtime.getRuntime().exec("cp " + fromFile + " " + toFile);
			p.waitFor();
			if (p.exitValue() != 0) {
				throw new Exception("Copy failed from file " + fromFile + " to " + toFile);
			}
			
		} catch (Exception e) {
			System.out.println("Cound not complete file write backup: "  + e.getMessage());
		}
		
	}
	
	/*
	 * Returns the path where to store files in sd card using the package name
	 */
	private static String getExternalFilesDir(String packageName) throws Exception {
		
		String state = Environment.getExternalStorageState();
		
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			
		    // We can read and write the media
			File folder = new File(Environment.getExternalStorageDirectory() + BACKUP_DIR + packageName + FILES_DIR);
			
			if (!folder.exists()) {
			    if (folder.mkdirs()) {
			    	return folder.getAbsolutePath();
			    } else {
			    	throw new Exception("Could not create dir: " + folder.getAbsolutePath());
			    }
			} else {
				return folder.getAbsolutePath();
			}
			
		} else {
			throw new Exception("Can not write to or SD card is not mounted");
		}
		
	}

}
