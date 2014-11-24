package edu.buffalo.cse.nsr.reptor;

import java.io.File;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import edu.buffalo.cse.nsr.reptor.RecorderTransformer;
import edu.buffalo.cse.nsr.reptor.Settings;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.Transform;

public class SootMain {

	public static void main(String[] args) {
		//System.setProperty("java.home", "C:\\Program Files\\Java\\jdk1.7.0_40");
		Settings.initialiseSoot();
		PackManager.v().getPack("wjtp").add(new Transform("wjtp.InstrumenterRecorder", new RecorderTransformer()));
		PackManager.v().runPacks();
		PackManager.v().writeOutput();
	}

}
