package com.matchscope.analysis;

import java.util.*;

public class MethodMatchResult extends MatchResult {

    private final String sourceClassName;
    private final String targetClassName;
    private final int sourceMethodNum;
    private final int targetMethodNum;
    protected final TreeMap<String, String> similar;
    protected final Set<String> sourceUnmatched;
    protected final Set<String> targetUnmatched;

    public MethodMatchResult(String sourceClassName, String targetClassName, int sourceMethodNum, int targetMethodNum) {
        super();
        this.sourceClassName = sourceClassName;
        this.targetClassName = targetClassName;
        this.sourceMethodNum = sourceMethodNum;
        this.targetMethodNum = targetMethodNum;
        this.similar = new TreeMap<>(new ClassMatcher.LengthAlphabeticalComparator());
        this.sourceUnmatched = new HashSet<>();
        this.targetUnmatched = new HashSet<>();

    }

    public boolean addSimilar(String source, String target) {
        return addToMatchMap(similar, source, target);
    }

    public TreeMap<String, String> getSimilar() {
        return similar;
    }


    public boolean isAllMethodsMatched() {
        return this.targetMethodNum == this.sourceMethodNum && getMatchSize() == this.sourceMethodNum;
    }

    public boolean isAllMethodIdentical() {
        return this.targetMethodNum == this.sourceMethodNum && this.getIdentical().size() == this.sourceMethodNum;
    }

    public String getSourceClassName() {
        return sourceClassName;
    }

    public String getTargetClassName() {
        return this.targetClassName;
    }

    @Override
    public Map<String, String> getMatches() {
        Map<String, String> ret = new TreeMap<>();
        ret.putAll(this.identical);
        ret.putAll(this.similar);
        return ret;
    }

    @Override
    public int getMatchSize() {
        return identical.size() + similar.size();
    }

    public void addSourceUnmatched(String s) {
        this.sourceUnmatched.add(s);
    }

    public void addTargetUnmatched(String s) {
        this.targetUnmatched.add(s);
    }

    public Set<String> getSourceUnmatched() {
        return sourceUnmatched;
    }

    public Set<String> getTargetUnmatched() {
        return targetUnmatched;
    }
}

