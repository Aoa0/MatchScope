package com.matchscope.obf;

import com.matchscope.CLIParser;
import com.matchscope.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ObfuscationAnalysis {
    // ToDo: add "v4" ?
    private static final Set<String> whiteList = new HashSet<>(Arrays.asList("os", "io", "ui"));
    private static Set<String> wordlist;

    public static ObfuscationLevel getObfuscationLevel(String className) {
        // no need to consider inner classes here

        ArrayList<Integer> obfuscationFlags = new ArrayList<>();
        String[] domains = className.split("\\.");
        if (domains.length == 1) {
            return ObfuscationLevel.FULLY;
        }
        for (String domain: domains) {
            if (isWordObfuscated(domain)) {
                obfuscationFlags.add(1);
            } else {
                obfuscationFlags.add(0);
            }
        }

        int obfuscatedNum = Utils.sumList(obfuscationFlags);
        if (obfuscatedNum == domains.length) {
            return ObfuscationLevel.FULLY;
        } else if (obfuscatedNum == 0) {
            return ObfuscationLevel.NON;
        } else if (obfuscatedNum == 1 && obfuscationFlags.get(obfuscationFlags.size() - 1) == 1) {
            return ObfuscationLevel.CLASSNAME;
        } else {
            if (obfuscationFlags.get(obfuscationFlags.size() - 1) == 0) {
                // if the classname is not obfuscated, then probably it's not obfuscated
//                try (FileWriter fw = new FileWriter("obfuscation.txt", true)){
//                    fw.write(className + "\n");
//                    fw.write(obfuscationFlags + " " + obfuscatedNum + "\n");
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
                return ObfuscationLevel.NON;
            }
            return ObfuscationLevel.PARTIALLY;
        }
    }

    public static boolean isWordObfuscated(String word) {
        if (wordlist == null) {
            wordlist = Utils.readLinesToSet(CLIParser.getWordlistPath());
        }
        assert wordlist != null;

        word = word.toLowerCase();
        if (word.length() >= 5) {
            return false;
        }

        if (word.length() <= 2 && !whiteList.contains(word)) {
            return true;
        }
        if (wordlist.contains(word)) {
            return false;
        }
        return true;
    }
}
