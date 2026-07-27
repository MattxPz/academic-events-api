package ec.edu.ups.academicevents.shared.audit;

import ec.edu.ups.academicevents.shared.security.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.UUID;

/**
 * Registra las operaciones críticas en {@code audit_logs}.
 *
 * <p>Cada método se ejecuta en una transacción independiente
 * ({@link Propagation#REQUIRES_NEW}) para que la traza sobreviva al rollback de la
 * transacción de negocio; sin eso sería imposible auditar operaciones fallidas como
 * un intento de inicio de sesión inválido.
 *
 * <p>La tabla tiene prohibido almacenar contraseñas, access tokens y refresh tokens.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAILED = "FAILED";

    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String CORRELATION_ID_ATTRIBUTE = AuditService.class.getName() + ".correlationId";
    private static final int MAX_IP_LENGTH = 45;
    private static final int MAX_ENDPOINT_LENGTH = 255;

    private final AuditLogRepository auditLogRepository;
    private final SecurityUtils securityUtils;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, String resourceType, Long resourceId,
                       String previousValueJson, String newValueJson, String result) {
        persist(securityUtils.currentUserId(), action, resourceType, resourceId,
                previousValueJson, newValueJson, result);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(String action, String resourceType, Long resourceId,
                              String previousValueJson, String newValueJson) {
        persist(securityUtils.currentUserId(), action, resourceType, resourceId,
                previousValueJson, newValueJson, RESULT_SUCCESS);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String action, String resourceType, Long resourceId,
                              String previousValueJson, String newValueJson) {
        persist(securityUtils.currentUserId(), action, resourceType, resourceId,
                previousValueJson, newValueJson, RESULT_FAILED);
    }

    /**
     * Variante para operaciones en las que el actor todavía no está en el contexto de
     * seguridad, como el inicio de sesión: la autenticación se completa después de auditar.
     * El actor indicado debe existir ya en la base de datos por la FK {@code fk_audit_logs_actor}.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccessAs(Long actorId, String action, String resourceType, Long resourceId,
                                String previousValueJson, String newValueJson) {
        persist(actorId, action, resourceType, resourceId, previousValueJson, newValueJson, RESULT_SUCCESS);
    }

    private void persist(Long actorId, String action, String resourceType, Long resourceId,
                         String previousValueJson, String newValueJson, String result) {
        HttpServletRequest request = currentRequest();

        AuditLog auditLog = AuditLog.builder()
                .actorId(actorId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .previousValue(previousValueJson)
                .newValue(newValueJson)
                .result(result)
                .ipAddress(request == null ? null : truncate(clientIp(request), MAX_IP_LENGTH))
                .httpMethod(request == null ? null : request.getMethod())
                .endpoint(request == null ? null : truncate(request.getRequestURI(), MAX_ENDPOINT_LENGTH))
                .correlationId(correlationId(request))
                .build();

        auditLogRepository.save(auditLog);
    }

    /**
     * Devuelve la petición HTTP en curso, o {@code null} si la operación se ejecuta
     * fuera de un hilo de petición (por ejemplo, una tarea programada).
     */
    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servletAttributes
                ? servletAttributes.getRequest()
                : null;
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * El identificador de correlación se genera una sola vez por petición y se reutiliza
     * en todas las trazas emitidas durante esa petición.
     */
    private String correlationId(HttpServletRequest request) {
        if (request == null) {
            return UUID.randomUUID().toString();
        }

        if (request.getAttribute(CORRELATION_ID_ATTRIBUTE) instanceof String existing) {
            return existing;
        }

        String correlationId = UUID.randomUUID().toString();
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        return correlationId;
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
