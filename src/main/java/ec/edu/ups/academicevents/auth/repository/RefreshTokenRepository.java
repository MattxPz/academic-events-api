package ec.edu.ups.academicevents.auth.repository;

import ec.edu.ups.academicevents.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenId(UUID tokenId);

    @Modifying
    @Query("update RefreshToken rt set rt.revokedAt = :now "
            + "where rt.user.id = :userId and rt.revokedAt is null")
    int revokeAllActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
