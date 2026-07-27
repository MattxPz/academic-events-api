package ec.edu.ups.academicevents.reports.repository;

import ec.edu.ups.academicevents.reports.dto.CertificateData;
import ec.edu.ups.academicevents.reports.dto.EventReportHeader;
import ec.edu.ups.academicevents.reports.dto.RegistrantRow;
import ec.edu.ups.academicevents.reports.dto.StatusCount;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Consultas de solo lectura de los reportes. Todas proyectan a records para no cargar
 * entidades completas, y viven aquí para no añadir métodos a los repositorios de otros módulos.
 */
@Repository
public class ReportQueryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    /** Inscritos de un evento ordenados por apellido. */
    public List<RegistrantRow> findRegistrants(Long eventId) {
        return entityManager.createQuery("""
                        select new ec.edu.ups.academicevents.reports.dto.RegistrantRow(
                            u.firstName, u.lastName, u.email, r.status, r.registeredAt, r.registrationCode)
                        from Registration r
                        join r.participant u
                        where r.event.id = :eventId
                        order by u.lastName asc, u.firstName asc
                        """, RegistrantRow.class)
                .setParameter("eventId", eventId)
                .getResultList();
    }

    /** Cabecera del reporte. El organizador se une por su identificador plano. */
    public Optional<EventReportHeader> findEventHeader(Long eventId) {
        return entityManager.createQuery("""
                        select new ec.edu.ups.academicevents.reports.dto.EventReportHeader(
                            e.id, e.title, e.modality, e.startAt, e.endAt,
                            e.capacity, e.availableCapacity,
                            e.organizerId, o.firstName, o.lastName)
                        from Event e
                        join User o on o.id = e.organizerId
                        where e.id = :eventId and e.deleted = false
                        """, EventReportHeader.class)
                .setParameter("eventId", eventId)
                .getResultList()
                .stream()
                .findFirst();
    }

    public Optional<CertificateData> findCertificateData(Long registrationId) {
        return entityManager.createQuery("""
                        select new ec.edu.ups.academicevents.reports.dto.CertificateData(
                            r.id, r.registrationCode, r.status,
                            u.id, u.firstName, u.lastName, u.email,
                            e.title, e.modality, e.startAt, e.endAt)
                        from Registration r
                        join r.participant u
                        join r.event e
                        where r.id = :registrationId
                        """, CertificateData.class)
                .setParameter("registrationId", registrationId)
                .getResultList()
                .stream()
                .findFirst();
    }

    /** Eventos por estado cuya fecha de inicio cae en el rango. */
    public List<StatusCount> countEventsByStatus(Instant from, Instant to, Long organizerId) {
        return entityManager.createQuery("""
                        select new ec.edu.ups.academicevents.reports.dto.StatusCount(e.status, count(e))
                        from Event e
                        where e.deleted = false
                          and e.startAt between :from and :to
                          and (:organizerId is null or e.organizerId = :organizerId)
                        group by e.status
                        """, StatusCount.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("organizerId", organizerId)
                .getResultList();
    }

    /** Inscripciones por estado solicitadas dentro del rango. */
    public List<StatusCount> countRegistrationsByStatus(Instant from, Instant to, Long organizerId) {
        return entityManager.createQuery("""
                        select new ec.edu.ups.academicevents.reports.dto.StatusCount(r.status, count(r))
                        from Registration r
                        where r.registeredAt between :from and :to
                          and (:organizerId is null or r.event.organizerId = :organizerId)
                        group by r.status
                        """, StatusCount.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("organizerId", organizerId)
                .getResultList();
    }

    public long countUniqueParticipants(Instant from, Instant to, Long organizerId) {
        Long total = entityManager.createQuery("""
                        select count(distinct r.participant.id)
                        from Registration r
                        where r.registeredAt between :from and :to
                          and (:organizerId is null or r.event.organizerId = :organizerId)
                        """, Long.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("organizerId", organizerId)
                .getSingleResult();

        return total == null ? 0L : total;
    }

    /**
     * Ocupación media de los eventos del rango, expresada en porcentaje.
     * Se excluyen los eventos sin capacidad para no dividir por cero.
     */
    public double averageOccupancyPercentage(Instant from, Instant to, Long organizerId) {
        Double average = entityManager.createQuery("""
                        select avg(1.0 * (e.capacity - e.availableCapacity) / e.capacity)
                        from Event e
                        where e.deleted = false
                          and e.capacity > 0
                          and e.startAt between :from and :to
                          and (:organizerId is null or e.organizerId = :organizerId)
                        """, Double.class)
                .setParameter("from", from)
                .setParameter("to", to)
                .setParameter("organizerId", organizerId)
                .getSingleResult();

        return average == null ? 0d : average * 100d;
    }
}
