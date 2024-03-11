package com.matchscope.analysis;

import com.matchscope.profile.ClassProfile;
import com.matchscope.profile.MethodProfile;

import java.util.*;

public class ClassDependencyAnalysis {

    public static Set<String> getDependencies(ClassProfile classProfile) {
        Set<String> dependencies = new TreeSet<>();

        // Class-inheritance Dependency
        String superClass = classProfile.getSuperClass();
        if (!superClass.equals("")) {
            dependencies.add(superClass);
        }
        // Field-in Dependency
        dependencies.addAll(classProfile.getFieldsType());

        // Method-prototype Dependency
        for (MethodProfile mp: classProfile.getMethodProfiles()) {
            dependencies.addAll(mp.getParameterTypes());
            dependencies.add(mp.getReturnType());
        }


        return dependencies;
    }
}
