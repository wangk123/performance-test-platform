package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.identity.AuthenticationException;
import com.yr.perftest.platform.identity.Principal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class FacadeGuard {
    public <T> T requirePrincipal(Supplier<T> action) {
        Principal principal = currentPrincipal();
        // audit hook placeholder (T10)
        // authorization hook placeholder (T2)
        return action.get();
    }

    public FacadeContext context() {
        return new FacadeContext(currentPrincipal());
    }

    private Principal currentPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Principal principal)) {
            throw new AuthenticationException("missing principal");
        }
        return principal;
    }
}
