package edu.buffalo.cse.nsr.reptor;

import java.io.FileOutputStream;
import java.util.Map;
import java.util.HashMap;

import soot.SootClass;
import soot.SootMethod;
import soot.Value;

public class FilesMetaData {
	
	// need to keep track of filename and associated output object
	
	Map<Value, String> fileMap;
	
	public FilesMetaData() {
		
		fileMap = new HashMap<Value, String>();
	}
	
	public String getFileName(Value fos) {
		
		if (fileMap.containsKey(fos)) {
			return fileMap.get(fos);
		}
		
		return null;
		
	}
	
	public void putFileName(String fn, Value fos) {
		
		fileMap.put(fos, fn);
		
	}

}
