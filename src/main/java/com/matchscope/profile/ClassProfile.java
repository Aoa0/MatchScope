package com.matchscope.profile;

import com.matchscope.obf.ObfuscationAnalysis;
import com.matchscope.obf.ObfuscationLevel;
import soot.SootClass;

import java.util.Map;
import java.util.TreeMap;

public class ClassProfile extends BasicClassProfile {
    private final ObfuscationLevel obfuscationLevel;
    private final Map<String, InnerClassProfile> innerClasses;
    private String classLevel0Hash = null;

    public ClassProfile(SootClass sootClass) {
        super(sootClass);
        this.obfuscationLevel = ObfuscationAnalysis.getObfuscationLevel(this.getName());
        this.innerClasses = new TreeMap<>();
    }

    private String calculateLevel0Hash() {
        StringBuilder basic = new StringBuilder(super.getLevel0Hash());
        for (InnerClassProfile innerClassProfile: innerClasses.values()) {
            basic.append("__");
            basic.append(innerClassProfile.getLevel0Hash());
        }

        return basic.toString();
    }

    public String getLevel0Hash() {
        if (classLevel0Hash == null) {
            classLevel0Hash = calculateLevel0Hash();
        }
        return classLevel0Hash;
    }

    public void updateClassProfile(InnerClassProfile subClass) {
        innerClasses.put(subClass.getName(), subClass);
    }

    public ObfuscationLevel getObfuscationLevel() {
        return this.obfuscationLevel;
    }

    public Map<String, InnerClassProfile> getInnerClasses() {
        return this.innerClasses;
    }


}
