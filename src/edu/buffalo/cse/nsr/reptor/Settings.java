package edu.buffalo.cse.nsr.reptor;

import java.util.ArrayList;
import java.util.Collections;

import soot.G;
import soot.Scene;
import soot.options.Options;


public class Settings {
	
	private static boolean SOOT_INITIALIZED = false;
	public final static String CLASSPATH = "src/";
	public final static String androidJAR = "./libs/android.jar";

	//public final static String apkFileLocation = "./apk/SharedPrefDemo.apk";
	//public final static String apkFileLocation = "./apk/cse622WriteTest.apk";
	//public final static String apkFileLocation = "./apk/AndroidsFortune-1.1.8-for-1.5.apk";
	public final static String apkFileLocation = "./apk/cse622test1.apk";
	
	public static void initialiseSoot(){
		if (SOOT_INITIALIZED)
			return;
		G.reset();
		Options.v().set_allow_phantom_refs(true);
		Options.v().set_whole_program(true);
		Options.v().set_prepend_classpath(true);
		Options.v().set_validate(true);

		//Options.v().set_output_format(Options.output_format_dex);
		Options.v().set_output_format(Options.output_format_jimple);
		Options.v().set_process_dir(Collections.singletonList(apkFileLocation));
		Options.v().set_force_android_jar(androidJAR);
		Options.v().set_src_prec(Options.src_prec_apk);
		Options.v().set_soot_classpath(CLASSPATH);
		
		Options.v().set_app(true);

		ArrayList<String> excludeList= new ArrayList<String>();
	    excludeList.add("android.");
	    excludeList.add("org.xmlpull");
	    Options.v().set_exclude(excludeList);

		Scene.v().loadNecessaryClasses();

		SOOT_INITIALIZED = true;
	}
}
