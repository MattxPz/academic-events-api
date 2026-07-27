package ec.edu.ups.academicevents.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record TokenResponse(
        @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.abc123")
        String accessToken,

        @Schema(example = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.def456")
        String refreshToken,

        @Schema(example = "Bearer")
        String tokenType,

        @Schema(example = "900")
        long expiresIn) {
}
