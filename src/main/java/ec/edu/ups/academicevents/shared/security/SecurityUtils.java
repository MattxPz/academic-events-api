package ec.edu.ups.academicevents.shared.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    private static final String ROLE_PREFIX = "ROLE_";

    public Long currentUserId() {
        AuthenticatedPrincipal principal = currentPrincipal();
        return principal == null ? null : principal.userId();
    }

    public String currentUserEmail() {
        AuthenticatedPrincipal principal = currentPrincipal();
        return principal == null ? null : principal.email();
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public boolean hasRole(String role) {
        Authentication authentication = getAuthentication();
        if (authentication == null) {
            return false;
        }
        String authority = role.startsWith(ROLE_PREFIX) ? role : ROLE_PREFIX + role;
        return authentication.getAuthorities().stream()
                .anyMatch(grantedAuthority -> grantedAuthority.getAuthority().equals(authority));
    }

    public boolean isAuthenticated() {
        Authentication authentication = getAuthentication();
        return authentication != null && authentication.isAuthenticated();
    }

    private Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private AuthenticatedPrincipal currentPrincipal() {
        Authentication authentication = getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
