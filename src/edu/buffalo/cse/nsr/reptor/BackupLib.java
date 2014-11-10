package edu.buffalo.cse.nsr.reptor;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.content.Context;
import android.content.Intent;

public class BackupLib {
	
	BackupLib() {
		
	}
	
	public void fileBackup() {
		
		Intent sendIntent = new Intent();
		sendIntent.setAction(Intent.ACTION_SEND);
		sendIntent.putExtra(Intent.EXTRA_TEXT, "This is my text to send.");
		sendIntent.setType("text/plain");
		//startActivity(sendIntent);
		 
	}
	
	public static void fileWriteBackup(FileOutputStream fos) {
		try {
			fos.flush();
			FileDescriptor fd = fos.getFD();
			FileInputStream fis = new FileInputStream(fd);
			
		} catch (Exception e) {
			System.out.println("Count not complete file write backup:"  + e.getMessage());
		}
		
		
	}

}
