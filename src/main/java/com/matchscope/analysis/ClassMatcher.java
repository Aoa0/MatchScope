package com.matchscope.analysis;

import com.matchscope.Utils;
import com.matchscope.obf.ObfuscationLevel;
import com.matchscope.profile.BasicClassProfile;
import com.matchscope.profile.ClassProfile;
import com.matchscope.profile.InnerClassProfile;
import com.matchscope.profile.MethodProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ClassMatcher {
    private final ClassMatchResult classMatchResult;
    private final TreeMap<String, String> matches;
    private final Map<String, TreeSet<String>> potentialMatches;

    private final Map<String, ClassProfile> sourceNotObfuscated;
    private final Map<String, ClassProfile> sourceObfuscated;
    private final Map<String, ClassProfile> source;

    private final Map<String, ClassProfile> targetNotObfuscated;
    private final Map<String, ClassProfile> targetObfuscated;
    private final Map<String, ClassProfile> target;
    private final Logger logger = LoggerFactory.getLogger(ClassMatcher.class);

    public ClassMatcher(Map<String, ClassProfile> source, Map<String, ClassProfile> target, ClassMatchResult classMatchResult) {
        this.classMatchResult = classMatchResult;
        this.potentialMatches = this.classMatchResult.getPotentialMatches();
        this.matches = this.classMatchResult.getMatches();

        this.source = source;
        this.target = target;

        this.sourceObfuscated = getObfuscatedClasses(source);
        this.sourceNotObfuscated = getNotObfuscatedClasses(source);

        this.targetObfuscated = getObfuscatedClasses(target);
        this.targetNotObfuscated = getNotObfuscatedClasses(target);
    }

    public void doMatch() {
        logger.info("Not Obfuscated Match.");
        matchNotObfuscated();
        logger.info("Not Obfuscated Match size: " + getMatchSize());

        Map<String, ClassProfile> sourceUnmatched = getUnmatchedClasses(source, matches.keySet());
        Map<String, ClassProfile> targetUnmatched = getUnmatchedClasses(target, new HashSet<>(matches.values()));

        logger.info("Source Unmatched Size: " + sourceUnmatched.size() + " Target Unmatched Size: " + targetUnmatched.size());

        IndexAwareMatch indexAwareMatch = new IndexAwareMatch(sourceUnmatched, targetUnmatched, this.classMatchResult);
        logger.info("Index-aware Match Size: " + this.matches.size());

        logger.info("Inner Class Match:");
        doInnerClassMatch();
        logger.info("Match size: " + getMatchSize());
    }

    public void processUnMatched() {

        logger.info("Pairwise Match.");
        doPairwiseMatch();
        logger.info("Pairwise Match size: " + getMatchSize());
        logger.info("Potential match size: " + potentialMatches.size());


        // some deleted/added class in index-aware are matched later. ToDo: the update to "deleted" should be done when adding new match
        this.classMatchResult.getDeleted().removeAll(this.classMatchResult.getMatches().keySet());
        this.classMatchResult.getDeleted().removeAll(this.classMatchResult.getPotentialMatches().keySet());
        this.classMatchResult.getAdded().removeAll(this.classMatchResult.getMatches().values());
        this.classMatchResult.getAdded().removeAll(this.classMatchResult.getTargetPotentialMatch());


        Set<String> sourceAll = getAllClass(source);
        Set<String> targetAll = getAllClass(target);

        sourceAll.removeAll(this.classMatchResult.getMatches().keySet());
        sourceAll.removeAll(this.classMatchResult.getPotentialMatches().keySet());

        this.classMatchResult.addDeletedAll(sourceAll);

        targetAll.removeAll(this.classMatchResult.getMatches().values());
        targetAll.removeAll(this.classMatchResult.getTargetPotentialMatch());

        this.classMatchResult.addAddedAll(targetAll);

//        this.classMatchResult.addDeletedAll(getUnmatchedSource());
//        this.classMatchResult.addAddedAll(getUnmatchedTarget());
//
//        System.out.println(this.classMatchResult.getDeleted().size());
//
//        Set<String> all = new HashSet<>();
//        all.addAll(this.classMatchResult.getDeleted());
//        all.addAll(this.classMatchResult.getPotentialMatches().keySet());
//        all.addAll(this.classMatchResult.getMatches().keySet());
//
//        deleted = new HashSet<>(this.classMatchResult.getDeleted());
//        deleted.retainAll(this.classMatchResult.getMatches().keySet());
//        System.out.println(deleted.size());
//        System.out.println(deleted);
//
//        deleted = new HashSet<>(this.classMatchResult.getDeleted());
//        deleted.retainAll(this.classMatchResult.getPotentialMatches().keySet());
//        System.out.println(deleted.size());
//        System.out.println(deleted);
//
//
//        Set<String> potential = new HashSet<>(this.classMatchResult.getPotentialMatches().keySet());
//        potential.retainAll(this.classMatchResult.getMatches().keySet());
//        System.out.println(potential.size());
//
//
//        System.out.println(all.size());
    }

    private Map<String, String> extendMatchedGroupsByIndex(Map<String, String> group) {
        // ToDo:
        return group;
    }

    private boolean isClassSimilarPrefilter(ClassProfile s, ClassProfile t) {
        if (s.getInnerClasses().size() != t.getInnerClasses().size()) {
            return false;
        }

        if (s.getInterfaces().size() != t.getInterfaces().size()) {
            return false;
        }
        return true;
    }

    private boolean isClassSimilar(String s, String t, double threshold) {
        ClassProfile scp = sourceObfuscated.get(s);
        ClassProfile tcp = targetObfuscated.get(t);
        return isClassSimilar(scp, tcp, threshold);
    }

    private boolean isClassSimilar(ClassProfile scp, ClassProfile tcp, double threshold) {
        String sourceL0FuzzyHash = scp.getLevel0FuzzyHash();
        String targetL0FuzzyHash = tcp.getLevel0FuzzyHash();
        double similarity = ClassComparator.calculateMethodSimilarityScore(sourceL0FuzzyHash, targetL0FuzzyHash);
        logger.debug("Comparing: " + scp.getName() + " " + tcp.getName() + " similarity: " + similarity);
        return similarity >= threshold;
    }


    private void doClassDependencyMatch() {
        Map<String, String> matchesCopy = new HashMap<>(matches);
        for (Map.Entry<String, String> entry: matchesCopy.entrySet()) {
            String s = entry.getKey();
            String t = entry.getValue();
            addMatchesWithClassDependency(source.get(s), target.get(t));
        }
    }

    private void doInnerClassMatch() {
        Map<String, String> matchesCopy = new TreeMap<>(matches);
        for (Map.Entry<String, String> entry: matchesCopy.entrySet()) {
            String s = entry.getKey();
            String t = entry.getValue();
            ClassProfile scp = source.get(s);
            ClassProfile tcp = target.get(t);
            if (scp == null || tcp == null) {
                System.out.println("NULL: " + s + " " + t);
            } else {
                TreeMap<String, InnerClassProfile> sourceInnerClasses = new TreeMap<>(new LengthAlphabeticalComparator());
                sourceInnerClasses.putAll(scp.getInnerClasses());
                TreeMap<String, InnerClassProfile> targetInnerClasses = new TreeMap<>(new LengthAlphabeticalComparator());
                targetInnerClasses.putAll(tcp.getInnerClasses());
                for (Iterator<Map.Entry<String, InnerClassProfile>> sourceIt = sourceInnerClasses.entrySet().iterator(); sourceIt.hasNext();) {
                    Map.Entry<String, InnerClassProfile> sourceEntry = sourceIt.next();
                    for (Iterator<Map.Entry<String, InnerClassProfile>> targetIt = targetInnerClasses.entrySet().iterator(); targetIt.hasNext();) {
                        Map.Entry<String, InnerClassProfile> targetEntry = targetIt.next();
                        if (compareByLevel0FuzzyHash(sourceEntry.getValue(), targetEntry.getValue(), Configs.innerClassSimilarityThreshold)) {
                            addToMatch(sourceEntry.getKey(), targetEntry.getKey());
                            targetIt.remove();
                            sourceIt.remove();
                            break;
                        }
                    }
                }
                logger.debug("Remaining size: " + sourceInnerClasses.size() + " " + targetInnerClasses);
            }
        }
    }

    private void addMatchesWithClassDependency(ClassProfile scp, ClassProfile tcp) {
        if (scp == null || tcp == null) {
            return;
        }

        Set<String> sDep = ClassDependencyAnalysis.getDependencies(scp);
        Set<String> tDep = ClassDependencyAnalysis.getDependencies(tcp);

        resolveDependencies(sDep, tDep);
    }

    private void doPairwiseMatch() {
        Map<String, String> matchedGroups = findMatchedGroups();
        for(String group: matchedGroups.keySet()) {
            Set<String> sourceClassProfiles = getClassesByPrefix(sourceObfuscated, group).keySet();
            sourceClassProfiles.removeAll(matches.keySet());
            Set<String> targetClassProfiles = getClassesByPrefix(targetObfuscated, group).keySet();
            targetClassProfiles.removeAll(matches.values());
//            if (sourceClassProfiles.size() == 1 && targetClassProfiles.size() == 1) {
//                addToMatch(sourceClassProfiles.iterator().next(), targetClassProfiles.iterator().next());
//            }
            for (String s: sourceClassProfiles) {
                for (String t: targetClassProfiles) {
                    ClassProfile scp = sourceObfuscated.get(s);
                    ClassProfile tcp = targetObfuscated.get(t);

                    if (isClassSimilarPrefilter(scp, tcp)) {
                        continue;
                    }
                    if (isClassSimilar(scp, tcp, Configs.pairwiseSimilarityThreshold)) {
                        addToPotentialMatch(s, t);
                    }
                }
            }
        }

//        handlePotentialMatches(false);

        Set<String> sourceRemaining = new TreeSet<>(source.keySet());
        sourceRemaining.removeAll(matches.keySet());
        logger.info("Source Remaining size: " + sourceRemaining.size());

        Set<String> targetRemaining = new TreeSet<>(target.keySet());
        targetRemaining.removeAll(matches.values());
        logger.info("Target Remaining size: " + targetRemaining.size());

        for (String s: sourceRemaining) {
            ClassProfile scp = source.get(s);
            for (String t: targetRemaining) {
                ClassProfile tcp = target.get(t);
                if (compareByLevel0FuzzyHash(scp, tcp, Configs.pairwiseSimilarityThreshold)) {
                    addToPotentialMatch(s, t);
                }
            }
        }
//        handlePotentialMatches(false);

    }

    private Map<String, String> findMatchedGroups() {
        Map<String, String> ret = new TreeMap<>();
        Set<String> partiallyObfuscated = getPartiallyObfuscatedClasses(source).keySet();
//        System.out.println(partiallyObfuscated);
        for (String s: partiallyObfuscated) {
            // Special case where no . exists in the classname;
            if(s.lastIndexOf('.') != -1){
                String packageName = s.substring(0, s.lastIndexOf('.') + 1);
                ret.put(packageName, packageName);
            }
        }

        for (Map.Entry<String, String> entry: matches.entrySet()) {
            String s = entry.getKey();
            String t = entry.getValue();
            if(s.lastIndexOf('.') != -1 && t.lastIndexOf('.') != -1) {
                String sPackage = s.substring(0, s.lastIndexOf('.') + 1);
                String tPackage = t.substring(0, t.lastIndexOf('.') + 1);
                ret.put(sPackage, tPackage);
            }
        }

        return ret;
    }

    private void handlePotentialMatches(boolean useIndex) {
        // handling one-to-many
        Iterator<Map.Entry<String, TreeSet<String>>> it = potentialMatches.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TreeSet<String>> entry = it.next();
            String source = entry.getKey();
            Set<String> targets = entry.getValue();
            if (targets.size() > 1) {
                logger.info(source + " multiple targets: " + targets);
                if (useIndex) {
                    String target = getStringWithLeastAlphabetDistance(source, targets);
                    addToMatch(source, target);
                }

            } else {
                addToMatch(source, targets.iterator().next());
                it.remove();
            }
        }

//        potentialMatches.clear();
    }

    private String getStringWithLeastAlphabetDistance(String target, Set<String> candidate) {
        String ret = "";
        int min = Integer.MAX_VALUE;
        for (String s: candidate) {
            int distance = calculateAlphabetDistance(target, s);
            if (distance < min) {
                min = distance;
                ret = s;
            }
        }
        return ret;
    }

    public static int calculateAlphabetDistance(String s1, String s2) {
        int ret = 0;
        for (int i = 0; i < s1.length(); i++) {
            char c1 = s1.charAt(i);
            char c2;
            try {
                c2 = s2.charAt(i);
            } catch (Exception e) {
                c2 = c1;
            }
            ret += (Math.abs(c1 - c2));

        }
        return ret;
    }




    public static boolean compareByLevel0FuzzyHash(BasicClassProfile s, BasicClassProfile t, double threshold) {
        return ClassComparator.compareByLevel0FuzzyHash(s, t, threshold);
    }

    private void resolveDependencies(Set<String> sDependencies, Set<String> tDependencies) {
        for (String s: sDependencies) {
            ClassProfile scp = source.get(s);
            if (scp != null && !Utils.isAndroidType(s)) {
                if (matches.containsKey(scp.getName())) {
                    continue;
                }
                for (String t: tDependencies) {
                    ClassProfile tcp = target.get(t);
                    if (tcp != null && !Utils.isAndroidType(t)) {
                        if (matches.containsKey(tcp.getName())) {
                            continue;
                        }
                        if (compareByLevel0FuzzyHash(scp, tcp, 0.7)) {
                            addToPotentialMatch(scp.getName(), tcp.getName());
                        }
                    }
                }
            }
        }
    }


    private void matchNotObfuscated() {
        Set<String> sn = sourceNotObfuscated.keySet();
        Set<String> tn = targetNotObfuscated.keySet();

        Set<String> intersection = new HashSet<>(sn);
        intersection.retainAll(tn);

        for (String s: intersection) {
            addToMatch(s, s);
        }

        for (String s: sn) {
            if (!intersection.contains(s)) {
                addToDelete(s);
            }
        }
        for (String s: tn) {
            if (!intersection.contains(s)) {
                addToAdded(s);
            }
        }
        
    }

    private void addToPotentialMatch(String s, String t) {
        if (potentialMatches.containsKey(s)) {
            Set<String> matched = potentialMatches.get(s);
            matched.add(t);
        } else {
            potentialMatches.put(s, new TreeSet<>(Collections.singleton(t)));
        }
    }

    private void addToMatch(String s, String t) {
        logger.debug("Add To Identical: " + s + " " + t);
        classMatchResult.addIdentical(s, t);
    }

    private void addToDelete(String s) {
        logger.debug("Add to deleted: " + s);
        classMatchResult.addDeleted(s);
    }

    private void addToAdded(String s) {
        logger.debug("Add to Added: " + s);
        classMatchResult.addAdded(s);
    }

    public static Map<String, Set<ClassProfile>> getFingerprintingMethodsByInstructionNum(Map<String, ClassProfile> m, int threshold) {
        Map<String, Set<ClassProfile>> ret = new HashMap<>();
        for (Map.Entry<String, ClassProfile> entry: m.entrySet()) {
            ClassProfile cp = entry.getValue();
            for (MethodProfile mp: cp.getMethodProfiles()) {
                if (mp.getStatementNum() >= threshold) {
                    Set<ClassProfile> targets = ret.computeIfAbsent(mp.getLevel2Hash(), k -> new HashSet<>());
                    targets.add(cp);
                }
            }
        }
        return ret;
    }

    public static Map<String, Set<ClassProfile>> getFingerprintingMethodsByConstantStrings(Map<String, ClassProfile> m) {
        Map<String, Set<ClassProfile>> ret = new HashMap<>();
        for (Map.Entry<String, ClassProfile> entry: m.entrySet()) {
            ClassProfile cp = entry.getValue();
            List<String> constantString = cp.getConstantStrings();
            if (constantString.size() > 0) {
                String hash = Utils.calculateHash(constantString);
                Set<ClassProfile> targets = ret.computeIfAbsent(hash, k -> new HashSet<>());
                targets.add(cp);
            }
        }
        return ret;
    }

    public static Map<String, BasicClassProfile> getAllUnmatchedClasses(Map<String, BasicClassProfile> m, Set<String> matched) {
        return m.entrySet().stream().filter(e->!matched.contains(e.getKey())).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public static Map<String, ClassProfile> getClassWithNoPackage(Map<String, ClassProfile> m) {
        return filterClasses(m, noPackage());
    }

    public static Map<String, ClassProfile> getClassesInPackage(Map<String, ClassProfile> m, String prefix) {
        // consider only classes in package and ignore classes in subpackages
        return filterClasses(m, inPackage(prefix));
    }

    public static Map<String, ClassProfile> getClassesByPrefix(Map<String, ClassProfile> m, String prefix) {
        return filterClasses(m, startsWith(prefix));
    }

    public static Map<String, ClassProfile> getFullyObfuscatedClasses(Map<String, ClassProfile> m) {
        return filterClasses(m, isFullyObfuscated());
    }

    public static Map<String, ClassProfile> getPartiallyObfuscatedClasses(Map<String, ClassProfile> m) {
        return filterClasses(m, isPartiallyObfuscated());
    }

    public static Map<String, ClassProfile> getNotObfuscatedClasses(Map<String, ClassProfile> m) {
        return filterClasses(m, notObfuscated());
    }

    public static Map<String, ClassProfile> getObfuscatedClasses(Map<String, ClassProfile> m) {
        return filterClasses(m, isObfuscated());
    }

    public static Map<String, ClassProfile> getUnmatchedClasses(Map<String, ClassProfile> m, Set<String> matched) {
        return filterClasses(m, notMatched(matched));
    }

    private static Map<String, ClassProfile> filterClasses(Map<String, ClassProfile> m,
                                                           Predicate<Map.Entry<String, ClassProfile>> predicate) {
        return m.entrySet().stream().filter(predicate).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Predicate<Map.Entry<String, ClassProfile>> notMatched(Set<String> s) {
        return e->!s.contains(e.getKey());
    }

    private static Predicate<Map.Entry<String, ClassProfile>> inPackage(String prefix) {
        return e->e.getValue().getName().startsWith(prefix) && e.getValue().getName().chars().filter(ch -> ch == '.').count() == prefix.chars().filter(ch -> ch == '.').count();
    }


    private static Predicate<Map.Entry<String, ClassProfile>> startsWith(String prefix) {
        return e->e.getValue().getName().startsWith(prefix);
    }

    private static Predicate<Map.Entry<String, ClassProfile>> noPackage() {
        return e->e.getValue().getName().chars().filter(ch -> ch == '.').count() == 0;
    }
    private static Predicate<Map.Entry<String, ClassProfile>> notObfuscated() {
        return e->e.getValue().getObfuscationLevel() == ObfuscationLevel.NON;
    }

    private static Predicate<Map.Entry<String, ClassProfile>> isObfuscated() {
        return e->e.getValue().getObfuscationLevel() != ObfuscationLevel.NON;
    }

    private static Predicate<Map.Entry<String, ClassProfile>> isPartiallyObfuscated() {
        return e -> e.getValue().getObfuscationLevel() == ObfuscationLevel.PARTIALLY ||
                e.getValue().getObfuscationLevel() == ObfuscationLevel.CLASSNAME;
    }

    private static Predicate<Map.Entry<String, ClassProfile>> isFullyObfuscated() {
        return e->e.getValue().getObfuscationLevel() == ObfuscationLevel.FULLY;
    }

    static class LengthAlphabeticalComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            // First, compare by length
            int lengthComparison = Integer.compare(s1.length(), s2.length());

            // If lengths are different, return the result
            if (lengthComparison != 0) {
                return lengthComparison;
            }

            // If lengths are the same, compare alphabetically
            return s1.compareTo(s2);
        }
    }

    public ClassMatchResult getClassMatchResult() {
        return classMatchResult;
    }

    public Map<String, String> getMatches() {
        return this.matches;
    }

    public int getMatchSize() {
        return classMatchResult.getMatchSize();
    }




    public int getAddedSize() {
        return classMatchResult.getAdded().size();
    }

    public int getDeletedSize() {
        return classMatchResult.getDeleted().size();
    }

    public Set<String> getAllClass(Map<String, ClassProfile> m) {
        Set<String> ret = new HashSet<>();
        for (Map.Entry<String, ClassProfile> entry: m.entrySet()) {
            ret.add(entry.getKey());
            ret.addAll(entry.getValue().getInnerClasses().keySet());
        }
        return ret;
    }

    public Set<String> getUnmatchedSource() {
        Set<String> ret = new HashSet<>();
        for (Map.Entry<String, ClassProfile> entry: source.entrySet()) {
            String className = entry.getKey();
            ClassProfile cp = entry.getValue();
            if (!matches.containsKey(className) && !potentialMatches.containsKey(className)) {
                ret.add(className);
            }
            if (cp.getInnerClasses().size() > 0) {
                for (String s: cp.getInnerClasses().keySet()) {
                    if (!matches.containsKey(s) && !potentialMatches.containsKey(s)) {
                        ret.add(s);
                    }
                }
            }
        }
        return ret;
    }

    public Set<String> getUnmatchedTarget() {
        Set<String> ret = new HashSet<>();
        for (Map.Entry<String, ClassProfile> entry: target.entrySet()) {
            String className = entry.getKey();
            ClassProfile cp = entry.getValue();
            if (!matches.containsValue(className) && !classMatchResult.getTargetPotentialMatch().contains(className)) {
                ret.add(className);
            }
            if (cp.getInnerClasses().size() > 0) {
                for (String s: cp.getInnerClasses().keySet()) {
                    if (!matches.containsKey(s) && !classMatchResult.getTargetPotentialMatch().contains(s)) {
                        ret.add(s);
                    }
                }
            }
        }
        return ret;
    }
}
