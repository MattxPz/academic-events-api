package ec.edu.ups.academicevents.shared.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByActorId(Long actorId, Pageable pageable);

    List<AuditLog> findByResourceTypeAndResourceId(String resourceType, Long resourceId);
}
