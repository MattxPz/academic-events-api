package ec.edu.ups.academicevents.sessions.service;

import ec.edu.ups.academicevents.events.entity.Event;
import ec.edu.ups.academicevents.events.repository.EventRepository;
import ec.edu.ups.academicevents.sessions.dto.SessionRequest;
import ec.edu.ups.academicevents.sessions.dto.SessionResponse;
import ec.edu.ups.academicevents.sessions.entity.Session;
import ec.edu.ups.academicevents.sessions.mapper.SessionMapper;
import ec.edu.ups.academicevents.sessions.repository.SessionRepository;
import ec.edu.ups.academicevents.shared.exception.BusinessRuleException;
import ec.edu.ups.academicevents.shared.exception.DuplicateResourceException;
import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.exception.ResourceNotFoundException;
import ec.edu.ups.academicevents.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final SessionRepository sessionRepository;
    private final EventRepository eventRepository;
    private final SessionMapper sessionMapper;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public SessionResponse create(Long eventId, SessionRequest request) {
        Event event = findActiveEventOrThrow(eventId);
        requireEventOwner(event);

        if (sessionRepository.existsByEventIdAndTitleAndStartAt(eventId, request.title(), request.startAt())) {
            throw new DuplicateResourceException(ErrorCode.DUPLICATE_RESOURCE,
                    "Ya existe una sesión con ese título e inicio para este evento.");
        }

        validateWithinEventRange(event, request);

        Session session = sessionMapper.toEntity(request, event);
        session = sessionRepository.save(session);
        return sessionMapper.toResponse(session);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> findByEvent(Long eventId) {
        findActiveEventOrThrow(eventId);

        return sessionRepository.findByEventIdOrderByStartAtAsc(eventId).stream()
                .map(sessionMapper::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SessionResponse findById(Long id) {
        return sessionMapper.toResponse(findActiveSessionOrThrow(id));
    }

    @Override
    @Transactional
    public SessionResponse update(Long id, SessionRequest request) {
        Session session = findActiveSessionOrThrow(id);
        Event event = session.getEvent();
        requireEventOwner(event);

        boolean titleOrStartChanged = !session.getTitle().equals(request.title())
                || !session.getStartAt().equals(request.startAt());

        if (titleOrStartChanged
                && sessionRepository.existsByEventIdAndTitleAndStartAt(
                        event.getId(), request.title(), request.startAt())) {
            throw new DuplicateResourceException(ErrorCode.DUPLICATE_RESOURCE,
                    "Ya existe una sesión con ese título e inicio para este evento.");
        }

        validateWithinEventRange(event, request);

        sessionMapper.updateEntity(session, request);
        session = sessionRepository.save(session);
        return sessionMapper.toResponse(session);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No se encontró la sesión solicitada."));

        requireEventOwner(session.getEvent());
        sessionRepository.delete(session);
    }

    private void validateWithinEventRange(Event event, SessionRequest request) {
        if (request.startAt().isBefore(event.getStartAt()) || request.endAt().isAfter(event.getEndAt())) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "La sesión debe estar dentro del rango de fechas del evento.");
        }
    }

    private Event findActiveEventOrThrow(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No se encontró el evento solicitado."));

        if (Boolean.TRUE.equals(event.getDeleted())) {
            throw new ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND, "No se encontró el evento solicitado.");
        }

        return event;
    }

    private Session findActiveSessionOrThrow(Long id) {
        Session session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No se encontró la sesión solicitada."));

        if (Boolean.TRUE.equals(session.getEvent().getDeleted())) {
            throw new ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND, "No se encontró la sesión solicitada.");
        }

        return session;
    }

    private void requireEventOwner(Event event) {
        if (!securityUtils.isAdmin() && !event.getOrganizerId().equals(securityUtils.currentUserId())) {
            throw new AccessDeniedException("Solo el organizador del evento puede gestionar sus sesiones.");
        }
    }
}
