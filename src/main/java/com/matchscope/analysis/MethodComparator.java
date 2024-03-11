package com.matchscope.analysis;

import com.matchscope.profile.MethodProfile;

public class MethodComparator extends BasicComparator{
    private final MethodProfile s;
    private final MethodProfile t;

    public MethodComparator(MethodProfile s, MethodProfile t) {
        this.s = s;
        this.t = t;
    }

    public boolean compare() {
        if (!s.getLevel0Hash().equals(t.getLevel0Hash())) {
            return false;
        }
        if (!s.getLevel1Hash().equals(t.getLevel1Hash())) {
            return false;
        }
        if (!s.getLevel2Hash().equals(t.getLevel2Hash())) {
            return false;
        }
        return true;
    }

    public boolean compareByLevel0FuzzyHush(double threshold) {
        return stringSimilar(s.getLevel0FuzzyHash(), t.getLevel0FuzzyHash(), threshold);
    }

    public boolean compareByLevel2FuzzyHash(double threshold) {
        return stringSimilar(s.getLevel2FuzzyHash(), t.getLevel2FuzzyHash(), threshold);
    }

    public double getLevel2FuzzyHashSimilarity() {
        return calculateMethodSimilarityScore(s.getLevel2FuzzyHash(), t.getLevel2FuzzyHash());
    }
}
