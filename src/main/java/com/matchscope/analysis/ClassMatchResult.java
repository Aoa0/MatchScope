package com.matchscope.analysis;

import java.util.*;

public class ClassMatchResult extends MatchResult {
    private final int sourceClassNum;
    private final int targetClassNum;
    private final TreeMap<String, TreeSet<String>> potentialMatches;


    public ClassMatchResult(int sourceClassNum, int targetClassNum) {
        super();
        this.sourceClassNum = sourceClassNum;
        this.targetClassNum = targetClassNum;
        this.potentialMatches = new TreeMap<>(new ClassMatcher.LengthAlphabeticalComparator());
    }

    @Override
    public TreeMap<String, String> getMatches() {
        return this.identical;
    }

    @Override
    public int getMatchSize() {
        return this.identical.size();
    }

    public int getTargetSize() {
        return new HashSet<>(this.identical.values()).size();
    }

    public TreeMap<String, TreeSet<String>> getPotentialMatches() {
        return potentialMatches;
    }

    public int getSourcePotentialMatchSize() {
        return potentialMatches.size();
    }

    public Set<String> getTargetPotentialMatch() {
        Set<String> matches = new HashSet<>();
        for (Set<String> s: this.potentialMatches.values()) {
            matches.addAll(s);
        }
        return matches;
    }

    public int getTargetPotentialMatchSize() {
        return getTargetPotentialMatch().size();
    }

    public int getInnerClassMatchSize() {
        int ret = 0;
        for (String s: identical.keySet()) {
            if (s.contains("$")) {
                ret++;
            }
        }
        return ret;
    }

    public int getPotentialInnerClassMatchSize() {
        int ret = 0;
        for (String s: potentialMatches.keySet()) {
            if (s.contains("$")) {
                ret++;
            }
        }
        return ret;
    }

    public int getClassMatchSize() {
        int ret = 0;
        for (String s: identical.keySet()) {
            if (!s.contains("$")) {
                ret++;
            }
        }
        return ret;
    }

    public int getPotentialClassMatchSize() {
        int ret = 0;
        for (String s: potentialMatches.keySet()) {
            if (!s.contains("$")) {
                ret++;
            }
        }
        return ret;
    }

    public int getSourceClassNum() {
        return sourceClassNum;
    }

    public int getTargetClassNum() {
        return targetClassNum;
    }

    public void removeDeleted(String s) {
        this.deleted.remove(s);
    }

    public void removeAdded(String s) {
        this.added.remove(s);
    }
}
