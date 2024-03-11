package com.matchscope.analysis;

import com.matchscope.Utils;
import com.matchscope.profile.BasicClassProfile;
import com.matchscope.profile.ClassProfile;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ClassComparator extends BasicComparator{
    private final ClassProfile c1;
    private final ClassProfile c2;
    private final Map<String, String> matches;

    public ClassComparator(ClassProfile c1, ClassProfile c2, Map<String, String> matches) {
        this.c1 = c1;
        this.c2 = c2;
        this.matches = matches;
    }

    public ClassComparator(ClassProfile c1, ClassProfile c2) {
        this.c1 = c1;
        this.c2 = c2;
        this.matches = null;
    }

    public boolean similar() {
        return compareByClassSignature() || compareByMethodNum();
    }

    public static boolean compareByConstantStrings(ClassProfile c1, ClassProfile c2) {
        List<String> sourceConstantStrings = c1.getConstantStrings();
        List<String> targetConstantStrings = c2.getConstantStrings();
        return sourceConstantStrings.equals(targetConstantStrings);
    }

    public boolean compareByClassSignature() {
        if (!c1.getClassType().equals(c2.getClassType())) {
            return false;
        }
        return !notSameSuperClass() && !notSameInterface();
    }

    private boolean notSameSuperClass() {
        String s1 = c1.getSuperClass();
        String s2 = c2.getSuperClass();
        return notSameClass(s1, s2);
    }

    private boolean notSameInterface() {
        List<String> l1 = c1.getInterfaces();
        List<String> l2 = c2.getInterfaces();
        if (l1.size() != l2.size()) {
            return true;
        }

        for (String s: c1.getInterfaces()) {
            for (String t: c2.getInterfaces()) {
                if (notSameClass(s, t)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean notSameClass(String s1, String s2) {
        if (s1.length() == 0 && s2.length()!= 0) {
            return true;
        } else if (s2.length() == 0 && s1.length() != 0) {
            return true;
        } else if (Utils.isAndroidClass(s1) && Utils.isAndroidClass(s2)) {
            return !s1.equals(s2);
        } else if (!Utils.isAndroidClass(s1) && !Utils.isAndroidClass(s2)) {
            return matches.containsKey(s1) && !matches.get(s1).equals(s2);
        }
        return false;
    }

    public boolean compareByMethodNum() {
        int n1 = c1.getMethodNum();
        int n2 = c2.getMethodNum();

        int max = Math.max(n1, n2);
        int min = Math.min(n1, n2);

        return (float) min/max >= 0.4;
    }

    public boolean compareByLevel0HashSimilarity(double threshold) {
        return stringSimilar(c1.getLevel0Hash(), c2.getLevel0Hash(), threshold);
    }

    public boolean compareByLevel0Hash() {
        return Objects.equals(c1.getLevel0Hash(), c2.getLevel0Hash());
    }

    public static boolean compareByLevel0FuzzyHash(BasicClassProfile scp, BasicClassProfile tcp, double threshold) {
        return stringSimilar(scp.getLevel0FuzzyHash(), tcp.getLevel0FuzzyHash(), threshold);
    }


//    public double compareByLevel1Hash() {
//        List<String> c1MethodLevel1Hashes = new ArrayList<>(c1.getMethodHashesLevel1());
//        List<String> c2MethodLevel1Hashes = new ArrayList<>(c2.getMethodHashesLevel1());
//
//        int count = 0;
//
//        for (String s: c1MethodLevel1Hashes) {
//            for (int i = 0; i < c2MethodLevel1Hashes.size(); i++) {
//                if (stringSimilar(s, c2MethodLevel1Hashes.get(i))) {
//                    count += 1;
//                    c2MethodLevel1Hashes.remove(i);
//                    break;
//                }
//            }
//        }
//
//        return (double) count / (c1MethodLevel1Hashes.size() + c2MethodLevel1Hashes.size());
//    }
}
