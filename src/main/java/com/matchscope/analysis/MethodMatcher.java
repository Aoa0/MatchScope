package com.matchscope.analysis;

import com.matchscope.Utils;
import com.matchscope.profile.BasicClassProfile;
import com.matchscope.profile.ClassProfile;
import com.matchscope.profile.MethodProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class MethodMatcher {
    private final ClassMatchResult classMatchResult;
    private final Map<String, ClassProfile> source;
    private final Map<String, ClassProfile> target;
    private final Map<String, MethodMatchResult> methodMatchResults;
    private final Map<String, String> potentialMatches;
    private final Map<String, String> classMatches;
    private final Map<String, String> newClassMatches;
    private final Logger logger = LoggerFactory.getLogger(MethodMatcher.class);

    public MethodMatcher(ClassMatchResult classMatchResult, Map<String, ClassProfile> source, Map<String, ClassProfile> target) {
        this.classMatchResult = classMatchResult;
        this.classMatches = classMatchResult.getMatches();
        this.source = source;
        this.target = target;
        this.methodMatchResults = new TreeMap<>(new ClassMatcher.LengthAlphabeticalComparator());
        this.potentialMatches = new HashMap<>();
        this.newClassMatches = new HashMap<>(this.classMatches);

    }

    public void doMatchUntilNoNewClassFound() {
        int count = 0;
        do {
            Map<String, String> toMatch = new HashMap<>(this.newClassMatches);
            this.newClassMatches.clear();
            doMatch(toMatch);
            count++;
        } while (newClassMatches.size() > 0 && count <= 5);

        logger.info("Class match size: " + classMatchResult.getMatchSize());
        handleDuplicate();
    }

    private void handleDuplicate() {
        // in some cases, the similar set may contain identical matches found later, we do deduplicate here
        for (Map.Entry<String, MethodMatchResult> entry: methodMatchResults.entrySet()) {
            MethodMatchResult mdf = entry.getValue();
            Map<String, String> similar = mdf.getSimilar();
            Map<String, String> identical = mdf.getIdentical();
            Set<String> added = mdf.getAdded();
            Set<String> deleted = mdf.getDeleted();
            similar.keySet().removeAll(identical.keySet());
            deleted.removeAll(identical.keySet());
        }
    }

    // firstly, find exact match in both classes
    // secondly, process multiple match in both classes
    // thirdly, process non match methods
    // fourthly, process remaining methods

    private void doMatch(Map<String, String> toMatch) {
        logger.info("To Method Match Class Num:" + toMatch.size());
        int classMatchedCount = 0;
        int nonMatchedCount = 0;
        for(Map.Entry<String, String> entry: toMatch.entrySet()) {
            String s = entry.getKey();
            String t = entry.getValue();
//            System.out.println("Processing: " + s + " " + t);
            BasicClassProfile scp = getClassProfile(s, source);
            BasicClassProfile tcp = getClassProfile(t, target);
            if (scp == null || tcp == null) {
                logger.debug("NULL: " + s + " " + t);
                continue;
            }

            List<MethodProfile> sourceProfiles = new ArrayList<>(scp.getMethodProfiles());
            List<MethodProfile> targetProfiles = new ArrayList<>(tcp.getMethodProfiles());

            int sourceProfilesSize = sourceProfiles.size();
            int targetProfilesSize = targetProfiles.size();

            MethodMatchResult methodMatchResult = methodMatchResults.computeIfAbsent(s, k -> new MethodMatchResult(s, t, sourceProfilesSize, targetProfilesSize));
            Map<String, String> matched = new TreeMap<>();

            // find exact match
            ListIterator<MethodProfile> sourceIterator = sourceProfiles.listIterator();
//            System.out.println(sourceProfiles + " " + targetProfiles);
            while (sourceIterator.hasNext()) {
                MethodProfile sourceProfile = sourceIterator.next();
                ListIterator<MethodProfile> targetIterator = targetProfiles.listIterator();
                while (targetIterator.hasNext()) {
                    MethodProfile targetProfile = targetIterator.next();
//                    System.out.println(sourceProfile.getName() + " " + targetProfile.getName());
                    if (compareMethods(sourceProfile, targetProfile)) {
                        addToIdentical(sourceProfile, targetProfile, methodMatchResult);
                        sourceIterator.remove();
                        targetIterator.remove();
                        matched.put(sourceProfile.getName(), targetProfile.getName());
                        break;
                    }
                }
            }

            // now handle the remaining methods
            sourceIterator = sourceProfiles.listIterator();
            while (sourceIterator.hasNext()) {
                MethodProfile sourceProfile = sourceIterator.next();
                ListIterator<MethodProfile> targetIterator = targetProfiles.listIterator();
                while (targetIterator.hasNext()) {
                    MethodProfile targetProfile = targetIterator.next();
                    double level2Similarity = getMethodsLevel2FuzzyHashSimilarity(sourceProfile, targetProfile);
                    if (level2Similarity >= Configs.methodLevel2FuzzyHashSimilarityThreshold) {
                        addToSimilar(sourceProfile, targetProfile, methodMatchResult);
                        sourceIterator.remove();
                        targetIterator.remove();
                        matched.put(sourceProfile.getName(), targetProfile.getName());
                        break;
                    } else if (compareMethodsByLevel0FuzzyHash(sourceProfile, targetProfile)) {
                        addToSimilar(sourceProfile, targetProfile, methodMatchResult);
                        sourceIterator.remove();
                        targetIterator.remove();
                        matched.put(sourceProfile.getName(), targetProfile.getName());
                        break;
                    }
                }
            }

            if (sourceProfiles.size() == 0 && targetProfiles.size() != 0) {
//                System.out.println(s + " " + t + " " + targetProfiles.size());
                for (MethodProfile mp: targetProfiles) {
                    addToAdded(mp, methodMatchResult);
                }
            }

            if (sourceProfiles.size() != 0 && targetProfiles.size() == 0) {
                for (MethodProfile mp: sourceProfiles) {
                    addToDeleted(mp, methodMatchResult);
                }
            }

            if (sourceProfiles.size() != 0 && targetProfiles.size() != 0) {
//                System.out.println(s + " " + t + " " + sourceProfiles.size() + " " + targetProfiles.size());

                for (MethodProfile mp: sourceProfiles) {
                    methodMatchResult.addSourceUnmatched(getMethodName(mp));
                }
                for (MethodProfile mp: targetProfiles) {
                    methodMatchResult.addTargetUnmatched(getMethodName(mp));
                }
            }

            if (sourceProfiles.size() == targetProfiles.size() && sourceProfiles.size() == 0) {
                classMatchedCount += 1;
            } else {
                if (matched.size() == 0) {
                    nonMatchedCount++;
                    logger.debug(s + " " + sourceProfilesSize + " " + t + " " + targetProfilesSize + " ");
                }
            }
        }
        logger.info("All Methods Matched Class Num: " + classMatchedCount);
        logger.info("Non Methods Matched Class Num: " + nonMatchedCount);

        handlePotential();
    }

    private void addToIdentical(String s, String t, String sourceClassName, String targetClassName) {
        BasicClassProfile sourceClassProfile = getClassProfile(sourceClassName, source);
        BasicClassProfile targetClassProfile = getClassProfile(targetClassName, target);
        if (sourceClassProfile == null || targetClassProfile == null) {
            logger.debug("NULL: " + sourceClassName + " " + targetClassName);
            return;
        }

        MethodMatchResult methodMatchResult = methodMatchResults.computeIfAbsent(sourceClassName,
                                                                            k -> new MethodMatchResult(sourceClassName,
                                                                                    targetClassName,
                                                                                    sourceClassProfile.getMethodProfiles().size(),
                                                                                    targetClassProfile.getMethodProfiles().size()));
        methodMatchResult.addIdentical(s, t);

    }

    private void addToIdentical(MethodProfile sourceMethod, MethodProfile targetMethod, MethodMatchResult methodMatchResult) {
        String s = getMethodName(sourceMethod);
        String t = getMethodName(targetMethod);

        if (methodMatchResult.addIdentical(s, t)) {
            addInvokedMethodsToPotential(sourceMethod, targetMethod);
        }
    }

    private void addToSimilar(MethodProfile sourceMethod, MethodProfile targetMethod, MethodMatchResult methodMatchResult) {
        String s = getMethodName(sourceMethod);
        String t = getMethodName(targetMethod);

        if (methodMatchResult.addSimilar(s, t)) {
            addInvokedMethodsToPotential(sourceMethod, targetMethod);
        }
    }

    private void addToDeleted(MethodProfile m, MethodMatchResult methodMatchResult) {
        methodMatchResult.addDeleted(getMethodName(m));
    }

    private void addToAdded(MethodProfile m, MethodMatchResult methodMatchResult) {
        methodMatchResult.addAdded(getMethodName(m));
    }

    private void addInvokedMethodsToPotential(MethodProfile sourceMethod, MethodProfile targetMethod) {
        List<String> sourceInvokedMethods = sourceMethod.getInvokedNonSystemMethod();
        if (sourceInvokedMethods.size() > 0) {
            List<String> targetInvokedMethods = targetMethod.getInvokedNonSystemMethod();
            if (sourceInvokedMethods.size() != targetInvokedMethods.size()) {
                return;
            }
            try {
                for (int i = 0; i < sourceInvokedMethods.size(); i++) {
                    potentialMatches.put(sourceInvokedMethods.get(i), targetInvokedMethods.get(i));
                }
            } catch (Exception exception) {
                logger.debug("Invoked Method Exception: " + sourceInvokedMethods + " " + targetInvokedMethods);
            }
        }
    }

    private void handlePotential() {
        for (Map.Entry<String, String> entry: potentialMatches.entrySet()) {
            String sourceSignature = entry.getKey();
            String targetSignature = entry.getValue();

            String sourceClassName = getClassNameFromSignature(sourceSignature);
            String sourceMethodName = getMethodNameFromSignature(sourceSignature);

            String targetClassName = getClassNameFromSignature(targetSignature);
            String targetMethodName = getMethodNameFromSignature(targetSignature);

            if (!(source.containsKey(sourceClassName) && target.containsKey(targetClassName))) {
                logger.debug("Class Not in Scope: " + sourceClassName + " " + targetClassName);
                continue;
            }

            if (classMatches.containsKey(sourceClassName)) {
                String value = classMatches.get(sourceClassName);
                if (!value.equals(targetClassName)) {
                    if (BasicComparator.calculateMethodSimilarityScore(source.get(sourceClassName).getLevel0FuzzyHash(), target.get(value).getLevel0FuzzyHash()) <
                    BasicComparator.calculateMethodSimilarityScore(source.get(sourceClassName).getLevel0FuzzyHash(), target.get(targetClassName).getLevel0FuzzyHash())) {
                        logger.debug("Different Match Found: " + sourceClassName + " " + targetClassName + " vs " + value);
                        classMatchResult.updateIdentical(sourceClassName, targetClassName);
                        methodMatchResults.remove(sourceClassName);
                        newClassMatches.put(sourceClassName, targetClassName);
                        addToIdentical(sourceMethodName, targetMethodName, sourceClassName, targetClassName);

                    }
                }
            } else {
                if (ClassComparator.compareByLevel0FuzzyHash(source.get(sourceClassName), target.get(targetClassName), Configs.classLevel0FuzzyHashSimilarityThreshold)) {
                    logger.debug("New Match Found: " + sourceClassName + " " + targetClassName);
                    classMatchResult.updateIdentical(sourceClassName, targetClassName);
                    methodMatchResults.remove(sourceClassName);
                    newClassMatches.put(sourceClassName, targetClassName);
                    addToIdentical(sourceMethodName, targetMethodName, sourceClassName, targetClassName);

                }
            }
        }
    }

    private String getClassNameFromSignature(String signature) {
        int index = signature.indexOf(' ');
        return signature.substring(0, index);
    }

    private String getMethodNameFromSignature(String signature) {
        int index = signature.indexOf(' ');
        return signature.substring(index + 1);
    }

    private String getMethodName(MethodProfile m) {
        return m.getSootMethod().getSubSignature();
//        return m.getSootMethod().getDeclaringClass() + "." + m.getSootMethod().getSignature();
    }

    private boolean compareMethods(MethodProfile s, MethodProfile t) {
        MethodComparator mc = new MethodComparator(s, t);
        return mc.compare();
    }

    private boolean compareMethodsByLevel0FuzzyHash(MethodProfile s, MethodProfile t) {
        MethodComparator mc = new MethodComparator(s, t);
        return mc.compareByLevel0FuzzyHush(Configs.methodLevel0FuzzyHashSimilarityThreshold);
    }

    private boolean compareMethodsByLevel2FuzzyHash(MethodProfile s, MethodProfile t) {
        MethodComparator mc = new MethodComparator(s, t);
        return mc.compareByLevel2FuzzyHash(Configs.methodLevel2FuzzyHashSimilarityThreshold);
    }

    private double getMethodsLevel2FuzzyHashSimilarity(MethodProfile s, MethodProfile t) {
        MethodComparator mc = new MethodComparator(s, t);
        return mc.getLevel2FuzzyHashSimilarity();
    }

    private BasicClassProfile getClassProfile(String className, Map<String, ClassProfile> classProfileMap) {
        if (className.contains("$")) {
            ClassProfile enclosingClass = classProfileMap.get(Utils.getEnclosingClass(className));
            if (enclosingClass != null) {
                return enclosingClass.getInnerClasses().get(className);
            } else {
                return null;
            }
        } else {
            return classProfileMap.get(className);
        }
    }

    public Map<String, MethodMatchResult> getMethodMatchResults() {
        return methodMatchResults;
    }

    public List<Integer> getMethodMatchStat() {
        int identical = 0;
        int similar = 0;
        int added = 0;
        int deleted = 0;
        int sourceUnmatched = 0;
        int targetUnmatched = 0;
        int allMethodsMatchedClassNum = 0;
        int allMethodsIdenticalClassNum = 0;

        for (Map.Entry<String, MethodMatchResult> entry: methodMatchResults.entrySet()) {
            MethodMatchResult mdf = entry.getValue();

            identical += mdf.getIdentical().size();
            similar += mdf.getSimilar().size();
            added += mdf.getAdded().size();
            deleted += mdf.getDeleted().size();
            sourceUnmatched += mdf.getSourceUnmatched().size();
            targetUnmatched += mdf.getTargetUnmatched().size();
            if (mdf.isAllMethodsMatched()) {
                allMethodsMatchedClassNum++;
            }
            if (mdf.isAllMethodIdentical()) {
                allMethodsIdenticalClassNum++;
            }
        }

        return new ArrayList<>(Arrays.asList(identical, similar, added, deleted, sourceUnmatched, targetUnmatched, allMethodsMatchedClassNum, allMethodsIdenticalClassNum));
    }
}
