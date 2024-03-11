package com.matchscope.analysis;

import com.matchscope.profile.ClassProfile;
import com.matchscope.Utils;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexAwareMatch {
    private final SortedMap<String, String> matches;
    private final Map<String, TreeSet<String>> potentialMatches;
    private final Map<String, ClassProfile> sourceClasses;
    private final Map<String, ClassProfile> targetClasses;
    private final Logger logger = LoggerFactory.getLogger(IndexAwareMatch.class);
    private final TreeSet<String> added;
    private final TreeSet<String> deleted;

    public IndexAwareMatch(Map<String, ClassProfile> sourceClasses, Map<String, ClassProfile> targetClasses, ClassMatchResult classMatchResult) {
        this.sourceClasses = sourceClasses;
        this.targetClasses = targetClasses;
        this.matches = classMatchResult.getMatches();
        this.potentialMatches = classMatchResult.getPotentialMatches();
        this.added = classMatchResult.getAdded();
        this.deleted = classMatchResult.getDeleted();
        getAnchorMatches();
        doIndexAwareMatch();
    }

    private void getAnchorMatches() {
        getUniqueMatchByLevel0Hash();
    }


    private void doIndexAwareMatch() {
        Map<String, String> groups = findMatchedGroup();
        for (Map.Entry<String, String> entry: groups.entrySet()) {
            String sourceGroup = entry.getKey();
            String targetGroup = entry.getValue();
            doIndexAwareMatchForGroup(sourceGroup, targetGroup);
        }
    }

    private void doIndexAwareMatchForGroup(String sourceGroup, String targetGroup) {
        SortedSet<String> sourceClassProfiles = new TreeSet<>(new ClassMatcher.LengthAlphabeticalComparator());
        SortedSet<String> targetClassProfiles = new TreeSet<>(new ClassMatcher.LengthAlphabeticalComparator());

        if (sourceGroup.equals("") && targetGroup.equals("")) {
//            logger.debug("Matching classes with no group");
            sourceClassProfiles.addAll(ClassMatcher.getClassWithNoPackage(sourceClasses).keySet());
            targetClassProfiles.addAll(ClassMatcher.getClassWithNoPackage(targetClasses).keySet());

        } else {
//            logger.debug("Matching: " + sourceGroup + " " + targetGroup);
            sourceClassProfiles.addAll(ClassMatcher.getClassesInPackage(sourceClasses, sourceGroup).keySet());
            targetClassProfiles.addAll(ClassMatcher.getClassesInPackage(targetClasses, targetGroup).keySet());
        }

        if (sourceClassProfiles.size() == 0 && targetClassProfiles.size() == 0) {
            // this is an empty package which contains no class but only subpackage, just ignore it
            return;
        }
        logger.debug("Group size: " + sourceClassProfiles.size() + " " + targetClassProfiles.size());
        List<Map.Entry<List<String>, List<String>>> splited = splitGroupByAnchor(sourceClassProfiles, targetClassProfiles);
        logger.debug("Splited size: " + splited.size());
        int count = 0;
        for (Map.Entry<List<String>, List<String>> entry: splited) {

            List<String> sourceList = new ArrayList<>(entry.getKey());
            List<String> targetList = new ArrayList<>(entry.getValue());

            if (sourceList.size() == 0 && targetList.size() == 0) {
                continue;
            }

            if (sourceList.size() == targetList.size()) {
                for (int i = 0; i < sourceList.size(); i++) {
                    String s = sourceList.get(i);
                    String t = targetList.get(i);
                    if (compareByLevel0FuzzyHash(s, t, Configs.classLevel0FuzzyHashSimilarityThreshold)) {
                        matches.put(s, t);
                    } else {
                        count++;
                    }
                }
            } else if (sourceList.size() == 0 && targetList.size() != 0){
                added.addAll(targetList);
            } else if (sourceList.size() != 0 && targetList.size() == 0) {
                deleted.addAll(sourceList);
            } else {
                ListIterator<String> sourceIt = sourceList.listIterator();
                while (sourceIt.hasNext()) {
                    String s = sourceIt.next();
                    ListIterator<String> targetIt = targetList.listIterator();

                    while (targetIt.hasNext()) {
                        String t = targetIt.next();
                        ClassProfile scp = sourceClasses.get(s);
                        ClassProfile tcp = targetClasses.get(t);
//                        System.out.println(scp.getName() + " " + tcp.getName() + " " + scp.getLevel0FeatureString() + " " + tcp.getLevel0FeatureString());
                        if (compareByLevel0FuzzyHash(scp, tcp, Configs.classLevel0FuzzyHashSimilarityThreshold)) {
                            matches.put(scp.getName(), tcp.getName());
                            sourceIt.remove();
                            targetIt.remove();
                            break;
                        }
                    }
                }
                if (sourceList.size() > 0 && targetList.size() == 0) {
                    deleted.addAll(sourceList);
                } else if (sourceList.size() == 0 && targetList.size() > 0) {
                    added.addAll(targetList);
                } else {
                    logger.debug("Unmatched: " + sourceList + " " + targetList);
                }

            }
        }
    }

    public boolean compareByLevel0FuzzyHash(String s, String t, double threshold) {
        ClassProfile scp = sourceClasses.get(s);
        ClassProfile tcp = targetClasses.get(t);
        return compareByLevel0FuzzyHash(scp, tcp, threshold);
    }

    public boolean compareByLevel0FuzzyHash(ClassProfile s, ClassProfile t, double threshold) {
        return ClassComparator.compareByLevel0FuzzyHash(s, t, threshold);
    }


    private void getUniqueMatchByLevel0Hash() {
        Map<String, String> sourceUniqueClasses = getUniqueLevel0Classes(this.sourceClasses);
        Map<String, String> targetUniqueClasses = getUniqueLevel0Classes(this.targetClasses);
        logger.info("Source Unique Class Num: " + sourceUniqueClasses.size());
        logger.info("Source Unique Class Num: " + targetUniqueClasses.size());
        for (Map.Entry<String, String> sourceEntry: sourceUniqueClasses.entrySet()) {
            String sourceClassName = sourceEntry.getKey();
            String sourceL0Hash = sourceEntry.getValue();
            for (Map.Entry<String, String> targetEntry: targetUniqueClasses.entrySet()) {

                if (targetEntry.getValue().equals(sourceL0Hash)) {
                    matches.put(sourceClassName, targetEntry.getKey());
                    break;
                }
            }
        }
        logger.info("Level0Hash Match size: " + matches.size());
    }

    private void getUniqueMatchByFingerprintingMethods() {
        Map<String, ClassProfile> sourceUnmatched = ClassMatcher.getUnmatchedClasses(sourceClasses, matches.keySet());
        Map<String, ClassProfile> targetUnmatched = ClassMatcher.getUnmatchedClasses(targetClasses, new HashSet<>(matches.values()));
        logger.info("Source Not Matched size: " + sourceUnmatched.size());
        logger.info("Target Not Matched size: " + targetUnmatched.size());

        Map<String, Set<ClassProfile>> sourceMethods = ClassMatcher.getFingerprintingMethodsByInstructionNum(sourceUnmatched, Configs.instructionNumThreshold);
        Map<String, Set<ClassProfile>> targetMethods = ClassMatcher.getFingerprintingMethodsByInstructionNum(targetUnmatched, Configs.instructionNumThreshold);
        logger.info("Source Instruction Fingerprinting Methods Num: "+ sourceMethods.size());
        logger.info("Target Instruction Fingerprinting Methods Num: "+ targetMethods.size());

        comparingFingerprinting(sourceMethods, targetMethods);
        logger.info("Fingerprint Match size: " + matches.size());
        logger.info("Potential Match size: " + potentialMatches.size());

        sourceMethods = ClassMatcher.getFingerprintingMethodsByConstantStrings(sourceUnmatched);
        targetMethods = ClassMatcher.getFingerprintingMethodsByConstantStrings(targetUnmatched);
        logger.info("Source Constant String Fingerprinting Methods Num: "+ sourceMethods.size());
        logger.info("Target Constant String Fingerprinting Methods Num: "+ targetMethods.size());

        comparingFingerprinting(sourceMethods, targetMethods);

        logger.info("Fingerprint Match size: " + matches.size());
        logger.info("Potential Match size: " + potentialMatches.size());


    }

    private void comparingFingerprinting(Map<String, Set<ClassProfile>> source, Map<String, Set<ClassProfile>> target) {
        for (String s: source.keySet()) {
            if (target.containsKey(s)) {
                Set<ClassProfile> sourceSet = source.get(s);
                Set<ClassProfile> targetSet = target.get(s);
                if (sourceSet.size() == 1 && targetSet.size() == 1) {
                    matches.put(sourceSet.iterator().next().getName(), targetSet.iterator().next().getName());
                } else {
                    for (ClassProfile scp : sourceSet) {
                        for (ClassProfile tcp : targetSet) {
                            if (ClassMatcher.compareByLevel0FuzzyHash(scp, tcp, Configs.classLevel0FuzzyHashSimilarityThreshold)) {
                                addToPotentialMatches(scp.getName(), tcp.getName());
                            }
                        }
                    }
                }
            }
        }
    }

    private void addToPotentialMatches(String s, String t) {
        Set<String> matched = potentialMatches.computeIfAbsent(s, k -> new TreeSet<>());
        matched.add(t);
    }

    private Map<String, String> getUniqueLevel0Classes(Map<String, ClassProfile> m) {
        Map<String, Set<String>> level0HashesMap = new HashMap<>();
        Map<String, String> ret = new HashMap<>();
        for (Map.Entry<String, ClassProfile> entry: m.entrySet()) {
            String className = entry.getKey();
            ClassProfile classProfile = entry.getValue();
            Set<String> classSet = level0HashesMap.computeIfAbsent(classProfile.getLevel0Hash(), k -> new TreeSet<>());
            classSet.add(className);
        }

        for (Map.Entry<String, Set<String>> entry: level0HashesMap.entrySet()) {
            if (entry.getValue().size() == 1) {
                ret.put(entry.getValue().iterator().next(), entry.getKey());
            }
        }

        return ret;
    }

    private Map<String, String> findMatchedGroup() {
        Map<String, String> ret = new TreeMap<>(new ClassMatcher.LengthAlphabeticalComparator());
        // for classes with no package
        ret.put("", "");
        Set<String> partiallyObfuscated = ClassMatcher.getPartiallyObfuscatedClasses(sourceClasses).keySet();
        for (String s: partiallyObfuscated) {
            if (s.lastIndexOf('.') != -1) {
                String packageName = s.substring(0, s.lastIndexOf('.') + 1);
                ret.put(packageName, packageName);
            }
        }

        for (Map.Entry<String, String> entry: matches.entrySet()) {
            String s = entry.getKey();
            String t = entry.getValue();
            if (s.lastIndexOf('.') != -1 && t.lastIndexOf('.') != -1) {
                String sPackage = s.substring(0, s.lastIndexOf('.') + 1);
                String tPackage = t.substring(0, t.lastIndexOf('.') + 1);
                ret.put(sPackage, tPackage);
            }
        }
        return ret;
    }

    private List<Map.Entry<List<String>, List<String>>> splitGroupByAnchor(SortedSet<String> sourceClasses, SortedSet<String> targetClasses) {
        Map<Integer, Integer> matchedIndex = getMatchedInGroup(sourceClasses, targetClasses);
        if (matchedIndex.size() > 0) {
            ensureAscending(matchedIndex);
        }

        List<Map.Entry<List<String>, List<String>>> m = new ArrayList<>();

        List<String> sourceClassesList = new ArrayList<>(sourceClasses);
        List<String> targetClassesList = new ArrayList<>(targetClasses);
        int lastSourceIndex = -1;
        int lastTargetIndex = -1;
        for (Map.Entry<Integer, Integer> entry: matchedIndex.entrySet()) {
            int sourceIndex = entry.getKey();
            int targetIndex = entry.getValue();
            m.add(new AbstractMap.SimpleEntry<>(sourceClassesList.subList(lastSourceIndex + 1, sourceIndex), targetClassesList.subList(lastTargetIndex + 1, targetIndex)));
//            System.out.println(entry.getKey() + " " + entry.getValue());
            lastSourceIndex = sourceIndex;
            lastTargetIndex = targetIndex;
        }

        return m;
    }

    private Map<Integer, Integer> getMatchedInGroup(SortedSet<String> sourceSet, SortedSet<String> targetSet) {
        Map<Integer, Integer> ret = new TreeMap<>();
        for (String s: sourceSet) {
            if (matches.containsKey(s)) {
                String t = matches.get(s);
                if (targetSet.contains(t)) {
                    ret.put(sourceSet.headSet(s).size(), targetSet.headSet(t).size());
                }
            }
        }
        return ret;
    }

    private void ensureAscending(Map<Integer, Integer> m) {
        // longest non-decreasing subsequence

        List<Integer> valueList = new ArrayList<>(m.values());
        List<Integer> ascending = Utils.getLongestNonDecreasingSubsequence(valueList);

        int index = 0;
        for (Iterator<Map.Entry<Integer, Integer>> it = m.entrySet().iterator(); it.hasNext();) {
            Map.Entry<Integer, Integer> entry = it.next();
            int value = entry.getValue();
            if (index > ascending.size() - 1) {
                it.remove();
            } else if (value == ascending.get(index)) {
                index++;
            } else {
                it.remove();
            }
        }
    }

}
