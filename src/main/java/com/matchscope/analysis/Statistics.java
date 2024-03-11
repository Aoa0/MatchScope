package com.matchscope.analysis;

import com.matchscope.Utils;
import com.matchscope.profile.BasicClassProfile;
import com.matchscope.profile.ClassProfile;
import com.matchscope.profile.MethodProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Statistics {
    private final Map<String, ClassProfile> fullyObfuscated;
    private final Map<String, ClassProfile> partiallyObfuscated;
    private final Map<String, ClassProfile> nonObfuscated;
    private final Map<String, ClassProfile> all;
    private final Logger logger = LoggerFactory.getLogger(Statistics.class);

    public Statistics(Map<String, ClassProfile> all) {
        this.all = all;
        this.fullyObfuscated = ClassMatcher.getFullyObfuscatedClasses(all);
        this.partiallyObfuscated = ClassMatcher.getPartiallyObfuscatedClasses(all);
        this.nonObfuscated = ClassMatcher.getNotObfuscatedClasses(all);
    }

    public void getStatistics() {
        getClassLevelStatistics();
        getMethodLevelStatistics();
    }

    public void getClassLevelStatistics() {
        int fullyNum = fullyObfuscated.size();
        int partiallyNum = partiallyObfuscated.size();
        int nonNum = nonObfuscated.size();

        int enumNum = 0;
        int interfaceNum = 0;
        int classNum = 0;
        int innerClassNum = 0;

        int allNum = all.size();
        for (ClassProfile cp: all.values()) {
            innerClassNum += cp.getInnerClasses().size();
            if (cp.isEnum()) {
                enumNum++;
            } else if (cp.isInterface()) {
                interfaceNum++;
            } else {
                classNum++;
            }
        }

        logger.info("ClassNum: " + classNum + " Inner ClassNum: " + innerClassNum + " Sum: " + (classNum + innerClassNum));
        logger.info("Fully obfuscated: " + fullyNum + " " + (float) fullyNum / allNum * 100 + "%" +
                " Non obfuscated: " + nonNum + " " + (float) nonNum / allNum * 100 + "%" +
                " Partially obfuscated: " + partiallyNum + " " + (float) partiallyNum / allNum * 100 + "%");

        logger.info("EnumNum: " + enumNum +
                " interfaceNum: " + interfaceNum +
                " allNum: " + allNum);
    }



    public void getMethodLevelStatistics() {
        List<Integer> methodStatementCountList = new ArrayList<>();
        Map<Integer, Integer> statementNumMap = new HashMap<>();

        int methodNum = 0;
        List<BasicClassProfile> allAndInnerClass = new ArrayList<>(all.values());
        for (ClassProfile cp: all.values()) {
            if (cp.getInnerClasses().size() > 0) {
                allAndInnerClass.addAll(cp.getInnerClasses().values());
            }
        }
        for (BasicClassProfile cp: allAndInnerClass) {
            methodNum += cp.getMethodProfiles().size();
            for (MethodProfile mp: cp.getMethodProfiles()) {

                int statementNum = mp.getStatementNum();
                methodStatementCountList.add(statementNum);
                Integer i = statementNumMap.getOrDefault(statementNum, 0);
                statementNumMap.put(statementNum, i + 1);
            }
        }


        Collections.sort(methodStatementCountList);

        logger.info("methodNum: " + methodNum +
                " methodStatementNum median: " + Utils.calculateMedian(methodStatementCountList) +
                " methodStatementNum average: " + Utils.calculateAverage(methodStatementCountList));
    }

}
