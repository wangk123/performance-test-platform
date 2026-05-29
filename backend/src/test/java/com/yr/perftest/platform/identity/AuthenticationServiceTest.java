package com.yr.perftest.platform.identity;

import com.yr.perftest.platform.TestSupport;

public final class AuthenticationServiceTest {
    private AuthenticationServiceTest() {
    }

    public static void runAll() {
        authenticatesEnabledUser();
        rejectsBadPassword();
        rejectsDisabledUser();
    }

    private static void authenticatesEnabledUser() {
        AuthenticationService service = new AuthenticationService();
        service.register("admin", "secret", "平台管理员", true, SystemRole.ADMIN);

        AuthenticatedUser user = service.authenticate("admin", "secret");

        TestSupport.assertEquals("admin", user.getUsername(), "authenticated username");
        TestSupport.assertTrue(user.hasRole(SystemRole.ADMIN), "authenticated user should have admin role");
    }

    private static void rejectsBadPassword() {
        AuthenticationService service = new AuthenticationService();
        service.register("owner", "correct", "项目负责人", true, SystemRole.PROJECT_OWNER);

        TestSupport.assertThrows(AuthenticationException.class, new TestSupport.ThrowingRunnable() {
            @Override
            public void run() {
                service.authenticate("owner", "wrong");
            }
        }, "bad password should be rejected");
    }

    private static void rejectsDisabledUser() {
        AuthenticationService service = new AuthenticationService();
        service.register("member", "secret", "项目成员", false, SystemRole.PROJECT_MEMBER);

        TestSupport.assertThrows(AuthenticationException.class, new TestSupport.ThrowingRunnable() {
            @Override
            public void run() {
                service.authenticate("member", "secret");
            }
        }, "disabled user should be rejected");
    }
}
