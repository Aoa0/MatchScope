package com.matchscope;

import com.matchscope.analysis.MatchAnalysis;
import com.matchscope.profile.AppProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParserException;
import soot.Scene;
import soot.jimple.infoflow.android.manifest.ProcessManifest;
import soot.options.Options;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class Executor {
    private String source;
    private String target;
    private String dir;
    private final String androidJar;

    private String sourceName;
    private String targetName;
    private AppProfile sourceProfile;
    private AppProfile targetProfile;
    private static final Logger logger = LoggerFactory.getLogger(Executor.class);

    public Executor(String source, String target, String androidJar) {
        this.source = source;
        this.target = target;
        this.sourceName = source.split("/")[source.split("/").length - 1];
        this.targetName = target.split("/")[target.split("/").length - 1];
        this.androidJar = androidJar;
        this.sourceProfile = getAPKProfile(source, androidJar);
        this.targetProfile = getAPKProfile(target, androidJar);
    }

    public Executor(String dir, String androidJar) {
        this.dir = dir;
        this.androidJar = androidJar;
    }

    public void run() {
        logger.info("Matching: " + sourceName + " " + targetName);
        MatchAnalysis matchAnalysis = new MatchAnalysis(sourceProfile, targetProfile);
    }

    public void runPairAnalysis() {
        File directory = new File(dir);
        ArrayList<String> apks = new ArrayList<>(Arrays.asList(Objects.requireNonNull(directory.list())));
        if (apks.size() < 2) {
            logger.error("Need no less than two apks");
            return;
        } else {
            logger.info(apks.size() + " apks need to be analysed");
        }

        //ToDo: may need a more strict comparator
        Collections.sort(apks);

        this.targetName = apks.get(0);
        this.target = dir + "/" + apks.get(0);
        this.targetProfile = getAPKProfile(target, androidJar);
        apks.remove(0);

        for(String apk : apks) {
            this.sourceName = this.targetName;
            this.source = this.target;
            this.sourceProfile = this.targetProfile;
            this.target = dir + "/" + apk;
            this.targetName = apk;
            this.targetProfile = getAPKProfile(target, androidJar);
            run();
        }
    }

    public AppProfile getAPKProfile(String apkPath, String androidJarPath) {
        setupSoot(apkPath, androidJarPath);
        try (ProcessManifest manifest = new ProcessManifest(apkPath)) {
            return new AppProfile(apkPath, manifest);
        } catch (XmlPullParserException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void setupSoot(String apkPath, String androidJarPath) {
        soot.G.reset();
        Options sootOpt = Options.v();
        sootOpt.set_keep_line_number(false);
        sootOpt.set_prepend_classpath(true);
        sootOpt.set_allow_phantom_refs(true);
        sootOpt.set_whole_program(true);
        sootOpt.set_src_prec(Options.src_prec_apk);
        sootOpt.set_process_dir(Collections.singletonList(apkPath));
        sootOpt.set_android_jars(androidJarPath);
        sootOpt.set_process_multiple_dex(true);
//        sootOpt.set_num_threads(16);
        sootOpt.set_output_format(Options.output_format_dex);

        Scene.v().loadBasicClasses();
        Scene.v().loadNecessaryClasses();
        Scene.v().loadDynamicClasses();
    }

}
