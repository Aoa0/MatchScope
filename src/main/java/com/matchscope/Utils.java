package com.matchscope;

import com.google.common.hash.Hashing;
import info.debatty.java.spamsum.SpamSum;
import soot.SootClass;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class Utils {
    public static boolean validateFile(String filePath) {
        return new File(filePath).isFile();
    }

    public static boolean validateDirectory(String filePath) {
        return new File(filePath).isDirectory();
    }

    public static Set<String> readLinesToSet(String filePath) {
//        System.out.println("readlinestoset");
        try (Stream<String> lines = Files.lines(Paths.get(filePath))) {
            return lines.collect(Collectors.toSet());
        } catch (IOException e) {
            return null;
        }
    }

    public static String getDividingLine(int size) {
        return String.join("", Collections.nCopies(size, "--"));
    }

    // ToDo: generated by chatGPT, maybe need to check?
    public static List<Integer> getLongestNonDecreasingSubsequence(List<Integer> list) {
        int n = list.size();
        int[] dp = new int[n];
        Arrays.fill(dp, 1);

        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (list.get(i) >= list.get(j)) {
                    dp[i] = Math.max(dp[i], dp[j] + 1);
                }
            }
        }

        int maxLengthIndex = 0;
        for (int i = 1; i < n; i++) {
            if (dp[i] > dp[maxLengthIndex]) {
                maxLengthIndex = i;
            }
        }

        List<Integer> longestSubsequence = new ArrayList<>();
        longestSubsequence.add(0, list.get(maxLengthIndex)); // Add the element at the beginning of the list
        int maxLength = dp[maxLengthIndex];
        for (int i = maxLengthIndex - 1; i >= 0; i--) {
            if (list.get(i) <= list.get(maxLengthIndex) && dp[i] == maxLength - 1) {
                longestSubsequence.add(0, list.get(i)); // Add the element at the beginning of the list
                maxLength--;
            }
        }

        return longestSubsequence;
    }

    public static boolean isListAscending(List<Integer> list) {
        for (int i = 0; i < list.size() - 1; i++) {
            if (list.get(i) > list.get(i + 1)) {
                return false;
            }
        }
        return true;
    }

    public static int sumList(ArrayList<Integer> list) {
        int ret = 0;
        for (Integer i: list) {
            ret += i;
        }
        return ret;
    }

    public static class Counter<T> {

        final ConcurrentMap<T, Integer> counts = new ConcurrentHashMap<>();

        public void put(T it) {
            add(it, 1);
        }

        public void add(T it, int v) {
            counts.merge(it, v, Integer::sum);
        }

        public List<T> mostCommon(int n) {
            return counts.entrySet().stream()
                    // Sort by value.
                    .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                    // Top n.
                    .limit(n)
                    // Keys only.
                    .map(Map.Entry::getKey)
                    // As a list.
                    .collect(Collectors.toList());
        }
    }

    public static void compareStringSet(Set<String> s1, Set<String> s2) {
        Set<String> s1Only = new HashSet<>(s1);
        Set<String> s2Only = new HashSet<>(s2);

        s1Only.removeAll(s2);
        s2Only.removeAll(s1);

        System.out.println("set1 size: " + s1.size());
        System.out.println("set2 size: " + s2.size());

        System.out.println("set1 only: " + s1Only.size() + " " + s1Only);
        System.out.println("set2 only: " + s2Only.size() + " " + s2Only);

    }

    public static int calculatePercentageDistance(int v1, int v2) {
        return Math.abs(v1 - v2)/(v1 + v2)*2;
    }

    public static <T extends Number> double calculateAverage(List<T> l) {
        double sum = 0;
        if (!l.isEmpty()) {
            for (T i: l) {
                sum += i.doubleValue();
            }
            return sum / l.size();
        }
        return sum;
    }

    public static <T extends Number> T calculateMedian(List<T> l) {
        if (!l.isEmpty()) {
            return l.get(l.size() / 2);
        } else {
            return null;
        }
    }

    public static boolean isResourceClass(String className) {
        return className.endsWith(".R");
    }

    public static boolean isResourceClass(SootClass sootClass) {
        String classSignature = sootClass.getName();
        return isResourceClass(classSignature);
    }

    public static boolean isAndroidClass(SootClass sootClass) {
        String classSignature = sootClass.getName();
        return isAndroidClass(classSignature);
    }

   public static boolean isAndroidClass(String className) {
        List<String> androidPrefixPkgNames = Arrays.asList("android.", "com.google.android.", "com.android.", "androidx.",
                                                            "kotlin.", "kotlinx.", "java.", "javax.", "sun.", "com.sun.", "jdk.", "j$.",
                                                            "org.omg.", "org.xml.", "org.w3c.dom");
        return androidPrefixPkgNames.stream().map(className::startsWith).reduce(false, (res, curr) -> res || curr);
    }


   public static String getRawType(String type) {
        return type.split("\\$")[0];
    }

    public static String getRawTypeWithoutArray(String type) {
        return type.replace("[]", "").split("\\$")[0];
    }


    public static String getEnclosingClass(String className) {
       return className.split("\\$")[0];
   }

   public static boolean isAndroidType(String type) {
        // "kotlin.", "com.google.android.",
        List<String> androidPrefixNames = Arrays.asList("android.", "androidx.", "java.", "javax.");
        for (String s: androidPrefixNames) {
            if (type.startsWith(s)) {
                return true;
            }
        }

        List<String> basicTypes = Arrays.asList("byte", "short", "int", "long", "float", "double", "boolean", "char", "void");
        type = getRawType(type);
        for (String s: basicTypes) {
            if (s.equals(type)) {
                return true;
            }
        }

        return false;
   }

    public static String getNormalizedType(String className) {
        if (isAndroidType(className)) {
            return getRawType(className);
        } else {
            return "X";
        }
    }

    public static String getNormalizedClassName(String className) {
        if (isAndroidClass(className)) {
            return className;
        } else {
            return "X";
        }
    }

   public static String dexClassType2Name(String classType ) {
        classType = classType.replace('/', '.');
        return classType.substring(1, classType.length()-1);
   }

   public static String calculateFuzzyHash(String s) {
       SpamSum spamSum = new SpamSum();
       return spamSum.HashString(s);
   }

   public static String calculateFuzzyHash(String delimiter, List<String> l) {
        String s = String.join(delimiter, l);
        return calculateFuzzyHash(s);
   }

   public static String calculateFuzzyHash(List<String> l) {
        return calculateFuzzyHash(" ", l);
   }

   public static String calculateHash(String s) {
        return Hashing.md5().hashString(s, StandardCharsets.UTF_8).toString();
   }

   public static String calculateHash(List<String> l) {
        return calculateHash(" ", l);
   }

   public static String calculateHash(String delimiter, List<String> l) {
        return calculateHash(String.join(delimiter, l));
   }

}
