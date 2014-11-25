package edu.buffalo.cse.nsr.reptor;

import java.io.File;

import android.os.Environment;

public class BackupGlobals {
	static final String CACHE_PATH = Environment.getExternalStorageDirectory().toString() + File.separator + "backup_cache";
	static final String DEFAULT_FILE_DATA = "data: ";
	public static final int REMOTE_READ = 0;
	public static final int REMOTE_WRITE = 1;
	public static final int REMOTE_READ_DONE = 2;
	public static final int REMOTE_WRITE_DONE = 3;
	public static final int REMOTE_READ_FAILED = 4;
	public static BackupLib mLib = null;
}
