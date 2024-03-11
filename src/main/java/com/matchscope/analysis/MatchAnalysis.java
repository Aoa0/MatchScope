package com.matchscope.analysis;

import com.matchscope.profile.AppProfile;
import com.matchscope.profile.ClassProfile;
import com.matchscope.Utils;
import com.matchscope.obf.ObfuscationLevel;
import com.matchscope.profile.MethodProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;
import java.util.stream.Collectors;

public class MatchAnalysis {
    private final AppProfile sourceProfile;
    private final AppProfile targetProfile;

    private Map<String, ClassProfile> sourceClasses;
    private Map<String, ClassProfile> targetClasses;

    private ClassMatchResult classMatchResult;

    private final Logger logger = LoggerFactory.getLogger(MatchAnalysis.class);


    public MatchAnalysis(AppProfile source, AppProfile target) {
        logger.info("Run MatchScope Analysis.");
        this.sourceProfile = source;
        this.targetProfile = target;
        this.sourceClasses = new HashMap<>(source.getAllClasses());
        this.targetClasses = new HashMap<>(target.getAllClasses());

        logger.info("Conduct Statistics Before Preprocessing.");
        conductStatistics();

        logger.info("Preprocessing.");
        preprocess();

        logger.info("Conduct Statistics After Preprocessing.");
        conductStatistics();

        classMatchResult = new ClassMatchResult(sourceClasses.size(), targetClasses.size());
        logger.info("Class Level Matching.");

        ClassMatcher cm = new ClassMatcher(sourceClasses, targetClasses, classMatchResult);
        cm.doMatch();

        logger.info("Method Level Matching.");
        MethodMatcher mm = new MethodMatcher(classMatchResult, sourceClasses, targetClasses);
        mm.doMatchUntilNoNewClassFound();

        // MethodMatcher may update the match
        // also handle the potential matches
        cm.processUnMatched();
        logger.info("MatchScope Analysis Done.");

        logger.info(Utils.getDividingLine(40));
        logger.info("Matched Num: " + classMatchResult.getMatchSize() + " " + classMatchResult.getTargetSize());
        logger.info("Matched Inner Class Num: " + classMatchResult.getInnerClassMatchSize());
        logger.info("Matched Class Num: " + classMatchResult.getClassMatchSize());
        logger.info("Potential Source Match Classes Num: " + classMatchResult.getSourcePotentialMatchSize());
        logger.info("Potential Target Match Classes Num: " + classMatchResult.getTargetPotentialMatchSize());
        logger.info("Added ClassNum: " + classMatchResult.getAddedSize());
        logger.info("Deleted ClassNum: " + classMatchResult.getDeletedSize());

        int obfNum = 0;
        int nonObfNum = 0;
        for (String s: classMatchResult.getMatches().keySet()) {
            ClassProfile cp = sourceClasses.get(s);
            if (cp != null) {
                if (cp.getObfuscationLevel() == ObfuscationLevel.NON) {
                    nonObfNum++;
                } else {
                    obfNum++;
                }
            }
        }
        for (String s: classMatchResult.getPotentialMatches().keySet()) {
            ClassProfile cp = sourceClasses.get(s);
            if (cp != null) {
                if (cp.getObfuscationLevel() == ObfuscationLevel.NON) {
                    nonObfNum++;
                } else {
                    obfNum++;
                }
            }
        }
        logger.info("Obfuscated Match Num: " + obfNum);
        logger.info("NonObf Match Num: " + nonObfNum);

        logger.info(Utils.getDividingLine(40));

        List<Integer> methodsNum = mm.getMethodMatchStat();

        logger.info("Identical Methods Num: " + methodsNum.get(0));
        logger.info("Similar methods num: " + methodsNum.get(1));
        logger.info("Added methods num: " + methodsNum.get(2));
        logger.info("Deleted methods num: " + methodsNum.get(3));
        logger.info("Source unmatched methods num: " + methodsNum.get(4));
        logger.info("Target unmatched methods num: " + methodsNum.get(5));
        logger.info("All methods matched class num: " + methodsNum.get(6));
        logger.info("All methods identical class num: " + methodsNum.get(7));

        int count = 0;
        for (String s: classMatchResult.getAdded()) {
            ClassProfile cp = targetClasses.get(s);
            if (cp != null) {
                count += cp.getMethodProfiles().size();
            }
        }
        logger.info("Added methods num in added classes: " + count);

        count = 0;
        for (String s: classMatchResult.getDeleted()) {
            ClassProfile cp = sourceClasses.get(s);
            if (cp != null) {
                count += cp.getMethodProfiles().size();
            }
        }
        logger.info("Deleted methods num in deleted classes: " + count);

        dumpAnalysisResultsToFile(cm.getClassMatchResult(), mm.getMethodMatchResults());
    }


    private void conductStatistics() {
        logger.info("Source App: " + Utils.getDividingLine(20));
        Statistics sourceStatistics = new Statistics(sourceClasses);
        sourceStatistics.getStatistics();
        logger.info("Target App: " + Utils.getDividingLine(20));
        Statistics targetStatistics = new Statistics(targetClasses);
        targetStatistics.getStatistics();
    }

    private void preprocess() {
        sourceClasses = removeLowEntropyClasses(sourceClasses);
        targetClasses = removeLowEntropyClasses(targetClasses);
    }

    private Map<String, ClassProfile> removeLowEntropyClasses(Map<String, ClassProfile> m) {
        return m.entrySet().stream().filter(e -> isClassInteresting(e.getValue())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<String, ClassProfile> removeClassesWithoutConstantStrings(Map<String, ClassProfile> m) {
        return m.entrySet().stream().filter(e -> e.getValue().getConstantStrings().size() > 0).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private boolean isClassInteresting(ClassProfile c) {
        return !isClassEmpty(c) &&
                !c.isInterface() &&
                !c.isEnum() &&
                !Utils.isAndroidClass(c.getName()) &&
                !Utils.isResourceClass(c.getName());
    }

    private boolean isClassEmpty(ClassProfile c) {
        int methodNum = c.getMethodNum();
        if (methodNum == 0) {
            return true;
        } else {
            for (MethodProfile mp : c.getMethodProfiles()) {
                if (mp.getStatementNum() != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    public void dumpAnalysisResultsToFile(ClassMatchResult classMatchResult, Map<String, MethodMatchResult> methodMatchResults) {
        int sourceVersionCode = this.sourceProfile.getVersionCode();
        int targetVersionCode = this.targetProfile.getVersionCode();

        String resultFileName = this.sourceProfile.getPackageName() + "__" + sourceVersionCode + "_" + targetVersionCode + ".match";
        try (PrintWriter out = new PrintWriter(resultFileName)) {
            out.println("Class Level:");
            out.println("Added Classes:");
            for (String s: classMatchResult.getAdded()) {
                out.println(s);
            }

            out.println("Deleted Classes:");
            for (String s: classMatchResult.getDeleted()) {
                out.println(s);
            }

            out.println("Matched Classes:");
            for (Map.Entry<String, String> entry: classMatchResult.getMatches().entrySet()) {
                out.write(entry.getKey() + " -> " + entry.getValue() + "\n");
            }
            out.println("\n\n\n");

            out.println("Method Level:");
            for (Map.Entry<String, MethodMatchResult> entry : methodMatchResults.entrySet()) {
                MethodMatchResult methodMatchResult = entry.getValue();
                out.println("Class: " + methodMatchResult.getSourceClassName() + " -> " + methodMatchResult.getTargetClassName());

                out.println("Identical:");
                for (Map.Entry<String, String> identicalEntry: methodMatchResult.getIdentical().entrySet()) {
                    out.println(identicalEntry.getKey() + " -> " + identicalEntry.getValue());
                }

                out.println("Similar:");
                for (Map.Entry<String, String> similarEntry: methodMatchResult.getSimilar().entrySet()) {
                    out.println(similarEntry.getKey() + " -> " + similarEntry.getValue());
                }

                out.println("Added:");
                for (String s: methodMatchResult.getAdded()) {
                    out.write(s);
                }

                out.println("Deleted:");
                for (String s: methodMatchResult.getDeleted()) {
                    out.println(s);
                }

                out.println("\n");
            }


        } catch (FileNotFoundException e) {
            logger.error("Write Result error");
        }



    }

}
