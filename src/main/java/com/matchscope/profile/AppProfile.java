package com.matchscope.profile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.Modifier;
import soot.PackManager;
import soot.Scene;
import soot.SootClass;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.util.Chain;
import com.matchscope.Utils;

import java.util.*;


public class AppProfile {
    private final String apkPath;
    private final Map<String, ClassProfile> allClasses;
    private final Chain<SootClass> sootClasses;
    private final String versionName;
    private final int versionCode;
    private final String packageName;
    private final Logger logger = LoggerFactory.getLogger(AppProfile.class);

    public AppProfile(String apkPath, ProcessManifest manifest) {
        this.apkPath = apkPath;
        this.packageName = manifest.getPackageName();
        this.versionName = manifest.getVersionName();
        this.versionCode = manifest.getVersionCode();

        logger.info("Profiling: " + apkPath);
        logger.info("package name: " + this.packageName);
        logger.info("version code: " + this.versionCode);
        logger.info("version name: " + this.versionName);

        logger.info("Run Soot Packs.");
        PackManager.v().runPacks();

        logger.info("Run MatchScope Profiler.");
        this.allClasses = new HashMap<>();
        this.sootClasses = Scene.v().getApplicationClasses();

        create();
    }


    private void create() {
        List<SootClass> toMerge = new ArrayList<>();
        for (SootClass clazz: sootClasses) {
            //ToDo: currently not analyzing these classes for efficiency
            if (clazz.isPhantomClass()
                    || Utils.isAndroidClass(clazz)
                    || Utils.isResourceClass(clazz)
                    || Modifier.isSynthetic(clazz.getModifiers())
            ) {
                continue;
            }
            String clazzName = clazz.getName();
            if (clazzName.contains("$")) {
                toMerge.add(clazz);
                continue;
            }

            ClassProfile classProfile = new ClassProfile(clazz);
            String className = classProfile.getName();
            allClasses.put(className, classProfile);
        }

        mergeInnerclass(toMerge);
    }

    private void mergeInnerclass(List<SootClass> toMerge) {
        for (SootClass clazz: toMerge) {
            String clazzName = clazz.getName();
            String enclosingClazz = Utils.getEnclosingClass(clazzName);
            InnerClassProfile innerClassProfile = new InnerClassProfile(clazz);

            ClassProfile enclosingProfile = findProfile(enclosingClazz);
            if (enclosingProfile != null) {
                enclosingProfile.updateClassProfile(innerClassProfile);
            }
        }
    }

    private ClassProfile findProfile(String name) {
        return allClasses.get(name);
    }

    public Map<String, ClassProfile> getAllClasses() {
        return allClasses;
    }

    public int getClassNum() {
        return allClasses.size();
    }

    public int getVersionCode() {
        return versionCode;
    }

    public String getVersionName() {
        return versionName;
    }

    public String getPackageName() {
        return packageName;
    }
}
