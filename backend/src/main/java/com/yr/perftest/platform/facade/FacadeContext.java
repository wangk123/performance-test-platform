package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.identity.Principal;

/**
 * Request-scoped facade context: current principal plus hook placeholders for audit/authz (T2/T10).
 */
public record FacadeContext(Principal principal) {
}
