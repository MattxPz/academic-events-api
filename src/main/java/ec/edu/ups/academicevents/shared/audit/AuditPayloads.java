package ec.edu.ups.academicevents.shared.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Construye los fragmentos JSON almacenados en {@code audit_logs.previous_value} y
 * {@code audit_logs.new_value}, con el mismo formato que los datos semilla de la tabla.
 *
 * <p>Nunca debe usarse para serializar contraseñas, access tokens ni refresh tokens.
 */
public final class AuditPayloads {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AuditPayloads() {
    }

    /** {@code {"status":"CONFIRMED"}} */
    public static String status(String status) {
        return of("status", status);
    }

    /** {@code {"email":"alguien@academic.test"}} */
    public static String email(String email) {
        return of("email", email);
    }

    /** {@code {"roles":["PARTICIPANT","ORGANIZER"]}} */
    public static String roles(List<String> roles) {
        return of("roles", roles);
    }

    public static String of(String key, Object value) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put(key, value);
        return toJson(payload);
    }

    private static String toJson(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("No se pudo serializar el detalle de auditoría.", ex);
        }
    }
}
