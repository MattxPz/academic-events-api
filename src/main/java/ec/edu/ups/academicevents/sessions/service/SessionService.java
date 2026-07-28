package ec.edu.ups.academicevents.sessions.service;

import ec.edu.ups.academicevents.sessions.dto.SessionRequest;
import ec.edu.ups.academicevents.sessions.dto.SessionResponse;

import java.util.List;

public interface SessionService {

    SessionResponse create(Long eventId, SessionRequest request);

    List<SessionResponse> findByEvent(Long eventId);

    SessionResponse findById(Long id);

    SessionResponse update(Long id, SessionRequest request);

    void delete(Long id);
}
