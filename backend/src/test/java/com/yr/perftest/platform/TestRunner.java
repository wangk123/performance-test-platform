package com.yr.perftest.platform;

import com.yr.perftest.platform.identity.AuthenticationServiceTest;
import com.yr.perftest.platform.project.ProjectServiceTest;
import com.yr.perftest.platform.script.JmeterScriptParserTest;
import com.yr.perftest.platform.script.JmeterScriptPatcherTest;
import com.yr.perftest.platform.script.JmeterScriptRendererTest;
import com.yr.perftest.platform.script.ScriptStepTypeTest;
import com.yr.perftest.platform.script.ThreadGroupConfigTest;

public class TestRunner {
    public static void main(String[] args) {
        AuthenticationServiceTest.runAll();
        ProjectServiceTest.runAll();
        ThreadGroupConfigTest.runAll();
        ScriptStepTypeTest.runAll();
        JmeterScriptParserTest.runAll();
        JmeterScriptPatcherTest.runAll();
        JmeterScriptRendererTest.runAll();
        System.out.println("All backend core tests passed.");
    }
}
