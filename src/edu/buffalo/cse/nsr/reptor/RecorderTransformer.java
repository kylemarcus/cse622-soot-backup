package edu.buffalo.cse.nsr.reptor;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

import soot.Body;
import soot.Local;
import soot.RefType;
import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootField;
import soot.SootMethod;
import soot.Unit;
import soot.Value;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.DefinitionStmt;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.JimpleBody;
import soot.jimple.StaticFieldRef;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;
import soot.util.Chain;

public class RecorderTransformer extends SceneTransformer {
	
	private static final String BACKUP_LIB_PACKAGE = "edu.buffalo.cse.nsr.reptor.BackupLib";
	private static final String INIT_METHOD = "initBackup";
	private static final String FILES_BACKUP_METHOD = "fileWriteBackup";
	private static final String FILES_RESTORE_METHOD = "fileOpenRestore";
	private static final String SHARED_PREFS_BACKUP_METHOD = "sharedPrefsBackup";
	private static final String SHARED_PREFS_RESTORE_METHOD = "sharedPrefsRestore";
	private static final String DATABASE_BACKUP_METHOD = "databaseBackup";
	private static final String DATABASE_RESTORE_METHOD = "databaseRestore";
	private static final String SHARED_PREF_REPLACE_METHOD = "sharedPrefsRestore";
	private static final String FILE_WRITE_SIG = "java.io.OutputStream: void write(byte[])";
	private static final String FILE_OPEN_READ_SIG = "java.io.FileInputStream openFileInput(java.lang.String)";
	private static final String FILE_OPEN_WRITE_SIG = "java.io.FileOutputStream openFileOutput(java.lang.String,int)";
	private static final String SHARED_PREFS_COMMIT_SIG = "android.content.SharedPreferences$Editor: boolean commit()";
	private static final String DATABASE_BACKUP_INSERT_SIG = "android.database.sqlite.SQLiteDatabase: long insert(java.lang.String,java.lang.String,android.content.ContentValues)";
	private static final String ON_CREATE_SIG = "onCreate(android.os.Bundle)";
	
	public RecorderTransformer() { }
	
	protected void compileLoadClasses(String location) {
		File recorderDir = new File(Settings.CLASSPATH + location);
		compileClasses(recorderDir.listFiles());
		loadClassesIntoScene(recorderDir.listFiles(), location.replace('/', '.'));
	}
	/**
	 * Compiles .java files into .class files.
	 *
	 * @param filesList - all files within a directory containing .java files
	 */
	protected void compileClasses(File[] filesList) {
		for (File f : filesList) {
			int index = f.getName().lastIndexOf('.');
			if (index > -1) {
				String extension = f.getName().substring(index, f.getName().length());
				if (extension.equals(".java")) {
					JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
					compiler.run(null, null, null, f.getPath());
				}
			}
		}
	}
	/**
	 * Loads compiled classes into the scene from .class files.
	 *
	 * @param filesList - all files within a directory containing .class files
	 * @param prepend - classpath to be prepended to the loaded classes
	 */
	protected void loadClassesIntoScene(File[] filesList, String prepend) {
		prepend = prepend.replaceAll("[.]+", ".");
		for (File f : filesList) {
			int index = f.getName().lastIndexOf('.');
			if (index > -1) {
				String fileName = f.getName().substring(0, index);
				String extension = f.getName().substring(index, f.getName().length());
				if (fileName.length() > 0 && extension.equals(".class")) {
					String className;
					if (prepend.length() == 0)
						className = fileName;
					else if (prepend.charAt(prepend.length() - 1) == '.')
						className = prepend + fileName;
					else
						className = prepend + '.' + fileName;
					if (className.contains("BackupLib") || className.contains("BackupGlobals")) {
						SootClass sc = Scene.v().forceResolve(className, SootClass.SIGNATURES);
						System.out.println("class: " + className + " methodCount: " + sc.getMethodCount() + " " + sc.isPhantom());
						sc.setApplicationClass();
					}
				}
			}
		}
	}
	@Override
	protected void internalTransform(String arg0, @SuppressWarnings("rawtypes") Map options) {
		//compileLoadClasses("edu/buffalo/cse/nsr/reptor");
		String packageName = Scene.v().getApplicationClasses().getFirst().getPackageName();
		compileLoadClasses(BackupLib.class.getPackage().getName().replace(".", "/"));
		FilesMetaData fileMetaData = new FilesMetaData();
		Chain<SootClass> applicationClasses = Scene.v().getApplicationClasses();
		for(SootClass c: applicationClasses){
			List<SootMethod> methods = c.getMethods();
			for(SootMethod m : methods){
				if(m.isConcrete()){
					Body body = m.retrieveActiveBody();
					Iterator<Unit> i = body.getUnits().snapshotIterator();
					while(i.hasNext()){
						Unit u = i.next();
						// Look for def statements, ie. = stmt
						if (u instanceof DefinitionStmt) {
							DefinitionStmt ds = (DefinitionStmt) u;
							if (ds.containsInvokeExpr()) {
								InvokeExpr invoke = ds.getInvokeExpr();
								// looks for openFileOutput with string as the fist arg, uses this to create map from fos -> filename
								// NOTE: this might need to be created first before running the code incase its out of order.
								if (invoke.getMethod().getSignature().contains(FILE_OPEN_WRITE_SIG)) {
									System.out.println(" ++++ CREATE FILE FOUND: " + invoke.getMethod().getSignature().toString());
									String s = invoke.getArg(0).toString();
									String fileName = s.substring(1, s.length()-1);
									System.out.println(fileName);
									Value l = ds.getLeftOp();
									fileMetaData.putFileName(fileName, l);
								}
								/*
								 * looks for FILE OPEN FOR READ, hooks code to copy from backup if not found
								 */
								if (invoke.getMethod().getSignature().contains(FILE_OPEN_READ_SIG)) {
									System.out.println(" ++++ OPEN FILE FOR READ FOUND: " + invoke.getMethod().getSignature().toString());
									Value fileName = invoke.getArg(0);
									// creates a list of units to add to source code
									List<Unit> generated = new ArrayList<Unit>();
									SootClass backupClassRef = Scene.v().getSootClass(BACKUP_LIB_PACKAGE);
									SootMethod fileOpenBackupMethod = backupClassRef.getMethodByName(FILES_RESTORE_METHOD);
									java.util.List<Value> l = new LinkedList<Value>();
									l.add(fileName);
									l.add(StringConstant.v(packageName));
									generated.add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(fileOpenBackupMethod.makeRef(), l)));
									// insert all units created
									body.getUnits().insertBefore(generated, u);
								}
							}
						}
						// look for invoke expressions, ie. just calling a method
						if (u instanceof InvokeStmt) {
							InvokeStmt invoke = (InvokeStmt)u;
							/*
							 * looks for SHARED PREFS COMMIT, hooks code to copy shared prefs files to backup
							 */
							if (invoke.getInvokeExpr().getMethod().getSignature().contains(SHARED_PREFS_COMMIT_SIG)) {
								System.out.println(" ++++ SHARED PREFS COMMIT FOUND: " + invoke.getInvokeExpr().getMethod().getSignature().toString());
								// creates a list of units to add to source code
								List<Unit> generated = new ArrayList<Unit>();
								SootClass backupClassRef = Scene.v().getSootClass(BACKUP_LIB_PACKAGE);
								SootMethod sharedPrefsBackupMethod = backupClassRef.getMethodByName(SHARED_PREFS_BACKUP_METHOD);
								java.util.List<Value> l = new LinkedList<Value>();
								l.add(StringConstant.v(packageName));
								generated.add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(sharedPrefsBackupMethod.makeRef(), l)));
								// insert all units created
								body.getUnits().insertAfter(generated, u);
							}
							
							if (invoke.getInvokeExpr().getMethod().getSignature().contains(DATABASE_BACKUP_INSERT_SIG)) {
								System.out.println(" ++++ DB Insert  FOUND: " + invoke.getInvokeExpr().getMethod().getSignature().toString());
								// creates a list of units to add to source code
								List<Unit> generated = new ArrayList<Unit>();
								SootClass backupClassRef = Scene.v().getSootClass(BACKUP_LIB_PACKAGE);
								SootMethod dataBaseBackupMethod = backupClassRef.getMethodByName(DATABASE_BACKUP_METHOD);
								java.util.List<Value> l = new LinkedList<Value>();
								l.add(StringConstant.v(packageName));
								generated.add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(dataBaseBackupMethod.makeRef(), l)));
								// insert all units created
								body.getUnits().insertAfter(generated, u);
							}
							
							if (invoke.getInvokeExpr().getMethod().getSignature().contains(ON_CREATE_SIG)) {
								
								System.out.println(" ++++ onCreate FOUND: " + invoke.getInvokeExpr().getMethod().getSignature().toString());
								// creates a list of units to add to source code
								List<Unit> generated = new ArrayList<Unit>();
								SootClass backupClassRef = Scene.v().getSootClass(BACKUP_LIB_PACKAGE);
								
								// new backupLib
								LocalGenerator lg = new LocalGenerator(body);
								Local wLocal = lg.generateLocal(backupClassRef.getType());
								Value wNewExpr = Jimple.v().newNewExpr(backupClassRef.getType());
								generated.add(Jimple.v().newAssignStmt(wLocal, wNewExpr));
								
								// get app context
								/*SootClass androidContextClass = Scene.v().getSootClass("android.content.ContextWrapper");
								Scene.v().loadNecessaryClasses();
								Local contextLocal = lg.generateLocal(androidContextClass.getType());
								
								SootClass contextClass = invoke.getInvokeExpr().getMethodRef().declaringClass();
								//SootMethod sm = Scene.v().getMethod("<android.content.Context getApplicationContext()>");
								SootMethod sm = androidContextClass.getMethod("<android.content.Context getApplicationContext()>");
								sm.setDeclaringClass(contextClass);
								VirtualInvokeExpr vinvokeExpr = Jimple.v().newVirtualInvokeExpr(contextLocal, sm.makeRef());
								generated.add(Jimple.v().newInvokeStmt(vinvokeExpr));*/
								
								//
								Local tmpRef = Jimple.v().newLocal("context", RefType.v("android.content.Context"));
							    body.getLocals().add(tmpRef);
							    SootMethod invokeContextMethod = Scene.v().getSootClass("android.content.ContextWrapper").getMethod("android.content.Context getApplicationContext()");
							    Local base = body.getThisLocal();
							    InvokeExpr invokeContext = Jimple.v().newVirtualInvokeExpr(base, invokeContextMethod.makeRef());
							    generated.add(Jimple.v().newAssignStmt(tmpRef, invokeContext));
								//
								
								// special invoke
							    
								SootMethod init = backupClassRef.getMethodByName(SootMethod.constructorName);
								java.util.List<Value> l = new LinkedList<Value>();
								l.add(tmpRef);
								Value wNewInit = Jimple.v().newSpecialInvokeExpr(wLocal, init.makeRef(), l);
								generated.add(Jimple.v().newInvokeStmt(wNewInit));
								
								//assign mlib
								SootField ref = Scene.v().getSootClass("edu.buffalo.cse.nsr.reptor.BackupGlobals").getFieldByName("mLib");
								StaticFieldRef sref = Jimple.v().newStaticFieldRef(ref.makeRef());
								generated.add(Jimple.v().newAssignStmt(sref, wLocal));
								
								// insert all units created
								body.getUnits().insertAfter(generated, u);
								
							}
							
							if (invoke.getInvokeExpr().getMethod().getSignature().contains(ON_CREATE_SIG)) {
								System.out.println(" ++++ On Create FOUND for shared Pref: " + invoke.getInvokeExpr().getMethod().getSignature().toString());
								// creates a list of units to add to source code
								List<Unit> generated = new ArrayList<Unit>();
								SootClass backupClassRef = Scene.v().getSootClass(BACKUP_LIB_PACKAGE);
								SootMethod replaceSharedPrefs = backupClassRef.getMethodByName(SHARED_PREF_REPLACE_METHOD);
								java.util.List<Value> l = new LinkedList<Value>();
								l.add(StringConstant.v(packageName));
								generated.add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(replaceSharedPrefs.makeRef(), l)));
								// insert all units created
								body.getUnits().insertAfter(generated, u);
							}
							
							if (invoke.getInvokeExpr().getMethod().getSignature().contains(ON_CREATE_SIG)) {
								System.out.println(" ++++ On Create FOUND for DB: " + invoke.getInvokeExpr().getMethod().getSignature().toString());
								// creates a list of units to add to source code
								List<Unit> generated = new ArrayList<Unit>();
								SootClass backupClassRef = Scene.v().getSootClass(BACKUP_LIB_PACKAGE);
								SootMethod restoreDB = backupClassRef.getMethodByName(DATABASE_RESTORE_METHOD);
								java.util.List<Value> l = new LinkedList<Value>();
								l.add(StringConstant.v(packageName));
								generated.add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(restoreDB.makeRef(), l)));
								// insert all units created
								body.getUnits().insertAfter(generated, u);
							}
							
							/*
							 * looks for FILE WRITE, hooks code to copy file to backup
							 */
							if (invoke.getInvokeExpr().getMethod().getSignature().contains(FILE_WRITE_SIG)) {
								System.out.println(" ++++ WRITE FOUND: " + invoke.getInvokeExpr().getMethod().getSignature().toString());
								// get byte array argument
								//Value b = invoke.getInvokeExpr().getArg(0);
								// get the calling object (FileOutputStream)
								Value b = ((InstanceInvokeExpr) invoke.getInvokeExpr()).getBase();
								System.out.println(fileMetaData.getFileName(b));
								// creates a list of units to add to source code
								List<Unit> generated = new ArrayList<Unit>();
								// create locals
								//LocalGenerator lg = new LocalGenerator(body);
								//class reference to backup lib
								//System.out.println("CP: " + Scene.v().getSootClassPath());
								//Scene.v().setSootClassPath(Scene.v().getSootClassPath()+":./libs");
								///SootClass backupClassRef = Scene.v().loadClassAndSupport("edu.buffalo.cse.nsr.reptor.BackupLib");
								SootClass backupClassRef = Scene.v().getSootClass(BACKUP_LIB_PACKAGE);
								///Scene.v().loadNecessaryClasses();
								//Scene.v().forceResolve("edu.buffalo.cse.nsr.reptor.BackupLib", SootClass.SIGNATURES);
								///backupClassRef.setApplicationClass();
								//int count = backupClassRef.getMethodCount();
								//System.out.println("Method count: " + count);
								// create a new class ref with local
								//Local wLocal = lg.generateLocal(backupClassRef.getType());
								//Value wNewExpr = Jimple.v().newNewExpr(backupClassRef.getType());
								// add it got the generated units
								//generated.add(Jimple.v().newAssignStmt(wLocal, wNewExpr));
								// special invoke
								//SootMethod init = backupClassRef.getMethodByName(SootMethod.constructorName);
								//Value wNewInit = Jimple.v().newSpecialInvokeExpr(wLocal, init.makeRef());
								//generated.add(Jimple.v().newInvokeStmt(wNewInit));
								// invoke method on new class
								//Local tmpRef = null;
								//SootMethod fileBackupMethod = backupClassRef.getMethodByName("fileBackup");
								//generated.add(Jimple.v().newInvokeStmt(Jimple.v().newVirtualInvokeExpr(wLocal, fileBackupMethod.makeRef())));
								// create a static call to backup method
								SootMethod fileBackupMethod2 = backupClassRef.getMethodByName(FILES_BACKUP_METHOD);
								java.util.List<Value> l = new LinkedList<Value>();
								l.add(b);
								String fn = fileMetaData.getFileName(b);
								l.add(StringConstant.v(fn));
								l.add(StringConstant.v(packageName));
								generated.add(Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(fileBackupMethod2.makeRef(), l)));
								// insert all units created
								body.getUnits().insertAfter(generated, u);
								//RefType parameter = RefType.v("android.content.Intent");
								//JNewExpr j = new JNewExpr(parameter);
								//generated.add(j);
								//LocalGenerator lg = new LocalGenerator(body);
								//lg.generateLocal(type);
								//SootMethod sm = Scene.v().getMethod("<android.content.Intent: Intent Intent()>");
								//VirtualInvokeExpr vinvokeExpr = Jimple.v().newVirtualInvokeExpr(localIntent, sm.makeRef());
							}
						}
						/*if (u.toString().contains("void write(byte[])")) {
System.out.println("++++ WRITE FOUND: " + u.toString());
NopStmt nop = Jimple.v().newNopStmt();
body.getUnits().insertAfter(nop, u);
}*/
					}
				}
			}
		}
	}
}