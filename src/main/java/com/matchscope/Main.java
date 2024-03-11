package com.matchscope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {

        CLIParser cliParser = new CLIParser(args);
        logger.info("SDK Path:  "+ cliParser.getAndroidJAR());

        if(cliParser.getAnalysisOption() == AnalysisOption.PAIR) {
            logger.info("Input: " + cliParser.getSourceAPK() + " " + cliParser.getTargetAPK());
            Executor executor = new Executor(cliParser.getSourceAPK(), cliParser.getTargetAPK(), cliParser.getAndroidJAR());
            executor.run();
        } else if (cliParser.getAnalysisOption() == AnalysisOption.DIRECTORY) {
            Executor executor = new Executor(cliParser.getTargetDir(), cliParser.getAndroidJAR());
            executor.runPairAnalysis();
        }
    }
}