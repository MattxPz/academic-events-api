package ec.edu.ups.academicevents.shared.security;

public record AuthenticatedPrincipal(Long userId, String email) {
}
