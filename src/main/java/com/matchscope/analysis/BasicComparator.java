package com.matchscope.analysis;

import info.debatty.java.stringsimilarity.Levenshtein;

public class BasicComparator {

    public static boolean stringSimilar(String m1, String m2, double threshold) {
        double similarity = calculateMethodSimilarityScore(m1, m2);
        return similarity >= threshold;
    }

    public static boolean stringSimilar(String m1, String m2) {
        return stringSimilar(m1, m2, 0.85);
    }

    public static double calculateMethodSimilarityScore(String m1, String m2) {
        return 1 - calculateLevenshteinDistance(m1, m2) / Math.max(m1.length(), m2.length());
    }

    private static double calculateLevenshteinDistance(String m1, String m2) {
        Levenshtein l = new Levenshtein();
        return l.distance(m1, m2);
    }
}
