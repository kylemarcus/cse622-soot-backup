package edu.buffalo.cse.nsr.reptor;

import java.io.File;
import java.io.FileOutputStream;
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
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.javaToJimple.LocalGenerator;
import soot.jimple.AssignStmt;
import soot.jimple.DefinitionStmt;
import soot.jimple.FieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Jimple;
import soot.jimple.NopStmt;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.VirtualInvokeExpr;
import soot.jimple.internal.JNewExpr;
import soot.util.Chain;

public class RecorderTransformer extends SceneTransformer{
	public RecorderTransformer() {
	}
	
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
                    
                    
                    if (className.contains("BackupLib")) {
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
//		compileLoadClasses("edu/buffalo/cse/nsr/reptor");
		
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
						
						if (u instanceof DefinitionStmt) {
							
							DefinitionStmt ds = (DefinitionStmt) u;
							
							if (ds.containsInvokeExpr()) {
								
								InvokeExpr invoke = ds.getInvokeExpr();
								
								// looks for openFileOutput with string as the fist arg
								// NOTE: this might need to be created first before running the code incase its out of order.
								if (invoke.getMethod().getSignature().contains("java.io.FileOutputStream openFileOutput(java.lang.String,int)")) {
									
									System.out.println(" ++++ CREATE FILE FOUND: " + invoke.getMethod().getSignature().toString());
									
									String s = invoke.getArg(0).toString();
									String fileName = s.substring(1, s.length()-1);
									
									System.out.println(fileName);
									
									Value l = ds.getLeftOp();
									
									fileMetaData.putFileName(fileName, l);
									
								}
							
							}
							
						}
						
						if (u instanceof InvokeStmt) {
							
							InvokeStmt invoke = (InvokeStmt)u;
							
							Boolean flag = Boolean.FALSE;
							if (invoke.getInvokeExpr().getMethod().getSignature().contains("Editor edit()")) {
								// flag == true
							}
							if (invoke.getInvokeExpr().getMethod().getSignature().contains("Editor edit()") && flag) {
								// flag == true
							}
							
							if (invoke.getInvokeExpr().getMethod().getSignature().contains("void write(byte[])")) {
								
								System.out.println(" ++++ WRITE FOUND: " + invoke.getInvokeExpr().getMethod().getSignature().toString());
								
								// get byte array argument
								//Value b = invoke.getInvokeExpr().getArg(0);
								
								// get the calling object (FileOutputStream)
								Value b = ((InstanceInvokeExpr) invoke.getInvokeExpr()).getBase();
								System.out.println(fileMetaData.getFileName(b));
								
								// creates a list of units to add to source code
								List<Unit> generated = new ArrayList<Unit>();
								
								// create locals
								LocalGenerator lg = new LocalGenerator(body);
								
								//class reference to backup lib
								//System.out.println("CP: " + Scene.v().getSootClassPath());
								//Scene.v().setSootClassPath(Scene.v().getSootClassPath()+":./libs");
								///SootClass backupClassRef = Scene.v().loadClassAndSupport("edu.buffalo.cse.nsr.reptor.BackupLib");
								SootClass backupClassRef = Scene.v().getSootClass("edu.buffalo.cse.nsr.reptor.BackupLib");
								///Scene.v().loadNecessaryClasses();
								//Scene.v().forceResolve("edu.buffalo.cse.nsr.reptor.BackupLib", SootClass.SIGNATURES);
								///backupClassRef.setApplicationClass();
								
								int count = backupClassRef.getMethodCount();
								System.out.println("Method count: " + count);
								
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
						        SootMethod fileBackupMethod2 = backupClassRef.getMethodByName("fileWriteBackup");
						        java.util.List l = new LinkedList();
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
