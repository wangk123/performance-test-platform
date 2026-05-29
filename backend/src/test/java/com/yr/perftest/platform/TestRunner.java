package com.yr.perftest.platform;

import com.yr.perftest.platform.identity.AuthenticationServiceTest;
import com.yr.perftest.platform.project.ProjectServiceTest;

public class TestRunner {
    public static void main(String[] args) {
        AuthenticationServiceTest.runAll();
        ProjectServiceTest.runAll();
        System.out.println("All backend core tests passed.");
    }
}
