package ec.edu.ups.academicevents.registrations.repository;

import ec.edu.ups.academicevents.registrations.entity.Registration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    boolean existsByEventIdAndParticipantId(Long eventId, Long participantId);

    @EntityGraph(attributePaths = {"event", "participant"})
    Page<Registration> findByParticipantId(Long participantId, Pageable pageable);

    @EntityGraph(attributePaths = {"event", "participant"})
    Page<Registration> findByParticipantIdAndStatus(Long participantId, String status, Pageable pageable);

    @EntityGraph(attributePaths = {"event", "participant"})
    Page<Registration> findByEventId(Long eventId, Pageable pageable);

    @EntityGraph(attributePaths = {"event", "participant"})
    Page<Registration> findByEventIdAndStatus(Long eventId, String status, Pageable pageable);

    Optional<Registration> findByRegistrationCode(UUID registrationCode);

    long countByEventIdAndStatus(Long eventId, String status);

    /**
     * Carga la inscripción junto con su evento y su participante para evitar accesos perezosos
     * fuera de la transacción (open-in-view está deshabilitado).
     */
    @EntityGraph(attributePaths = {"event", "participant"})
    Optional<Registration> findWithDetailsById(Long id);

    /**
     * Listado administrativo con filtros opcionales; usa JOIN FETCH para evitar el problema N+1.
     */
    @Query(value = """
            select r
            from Registration r
            join fetch r.event e
            join fetch r.participant p
            where (:eventId is null or e.id = :eventId)
              and (:status is null or r.status = :status)
            """,
            countQuery = """
            select count(r)
            from Registration r
            where (:eventId is null or r.event.id = :eventId)
              and (:status is null or r.status = :status)
            """)
    Page<Registration> search(@Param("eventId") Long eventId, @Param("status") String status, Pageable pageable);
}
