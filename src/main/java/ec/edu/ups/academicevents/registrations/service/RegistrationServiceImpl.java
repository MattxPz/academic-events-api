package ec.edu.ups.academicevents.registrations.service;

import ec.edu.ups.academicevents.events.entity.Event;
import ec.edu.ups.academicevents.events.repository.EventRepository;
import ec.edu.ups.academicevents.registrations.dto.RegistrationRequest;
import ec.edu.ups.academicevents.registrations.dto.RegistrationResponse;
import ec.edu.ups.academicevents.registrations.dto.RegistrationStatusRequest;
import ec.edu.ups.academicevents.registrations.entity.Registration;
import ec.edu.ups.academicevents.registrations.mapper.RegistrationMapper;
import ec.edu.ups.academicevents.registrations.repository.RegistrationRepository;
import ec.edu.ups.academicevents.shared.exception.BusinessRuleException;
import ec.edu.ups.academicevents.shared.exception.DuplicateResourceException;
import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.exception.NoCapacityException;
import ec.edu.ups.academicevents.shared.exception.ResourceNotFoundException;
import ec.edu.ups.academicevents.shared.security.SecurityUtils;
import ec.edu.ups.academicevents.users.entity.User;
import ec.edu.ups.academicevents.users.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationServiceImpl.class);

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String STATUS_CANCELLED = "CANCELLED";
    private static final String EVENT_STATUS_PUBLISHED = "PUBLISHED";

    /** Un intento inicial más dos reintentos ante conflictos de bloqueo optimista. */
    private static final int MAX_STATUS_UPDATE_ATTEMPTS = 3;

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final RegistrationMapper registrationMapper;
    private final SecurityUtils securityUtils;
    private final PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @PostConstruct
    void initTransactionTemplate() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional
    public RegistrationResponse create(RegistrationRequest request) {
        Long participantId = securityUtils.currentUserId();
        Event event = findActiveEventOrThrow(request.eventId());
        Instant now = Instant.now();

        if (!EVENT_STATUS_PUBLISHED.equals(event.getStatus())) {
            throw new BusinessRuleException(ErrorCode.EVENT_NOT_OPEN,
                    "El evento no está publicado, por lo que no admite inscripciones.");
        }

        if (now.isBefore(event.getRegistrationStartAt()) || now.isAfter(event.getRegistrationEndAt())) {
            throw new BusinessRuleException(ErrorCode.REGISTRATION_WINDOW_CLOSED,
                    "El período de inscripción del evento no está abierto.");
        }

        if (!event.getEndAt().isAfter(now)) {
            throw new BusinessRuleException(ErrorCode.EVENT_FINISHED,
                    "El evento ya finalizó, no admite nuevas inscripciones.");
        }

        if (registrationRepository.existsByEventIdAndParticipantId(event.getId(), participantId)) {
            throw new DuplicateResourceException(ErrorCode.ALREADY_REGISTERED,
                    "Ya existe una inscripción del participante en este evento.");
        }

        if (!hasAvailableCapacity(event)) {
            throw new NoCapacityException(ErrorCode.NO_CAPACITY,
                    "El evento no tiene cupos disponibles.");
        }

        User participant = userRepository.findById(participantId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No se encontró el usuario autenticado."));

        Registration registration = Registration.builder()
                .registrationCode(UUID.randomUUID())
                .event(event)
                .participant(participant)
                .status(STATUS_PENDING)
                .statusUpdatedAt(now)
                .build();

        try {
            registration = registrationRepository.saveAndFlush(registration);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Conflicto de unicidad al inscribir al participante {} en el evento {}",
                    participantId, event.getId(), ex);
            throw new DuplicateResourceException(ErrorCode.ALREADY_REGISTERED,
                    "Ya existe una inscripción del participante en este evento.");
        }

        return registrationMapper.toResponse(registration);
    }

    /**
     * El cambio de estado se ejecuta con {@link TransactionTemplate} en lugar de {@code @Transactional}
     * porque cada reintento necesita una transacción y un contexto de persistencia nuevos: tras un fallo
     * de bloqueo optimista la transacción original queda marcada para rollback y el evento debe releerse.
     */
    @Override
    public RegistrationResponse updateStatus(Long id, RegistrationStatusRequest request) {
        OptimisticLockingFailureException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_STATUS_UPDATE_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> applyStatusChange(id, request));
            } catch (OptimisticLockingFailureException ex) {
                lastFailure = ex;
                log.warn("Conflicto de concurrencia al actualizar la inscripción {} (intento {} de {})",
                        id, attempt, MAX_STATUS_UPDATE_ATTEMPTS);
            }
        }

        throw lastFailure;
    }

    @Override
    @Transactional
    public RegistrationResponse cancel(Long id) {
        Registration registration = findRegistrationOrThrow(id);

        if (!securityUtils.isAdmin() && !registration.getParticipant().getId().equals(securityUtils.currentUserId())) {
            throw new AccessDeniedException("La inscripción pertenece a otro participante.");
        }

        if (STATUS_CANCELLED.equals(registration.getStatus()) || STATUS_REJECTED.equals(registration.getStatus())) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "La inscripción ya está " + registration.getStatus() + " y no puede cancelarse.");
        }

        Event event = registration.getEvent();
        Instant now = Instant.now();

        if (!event.getStartAt().isAfter(now)) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "El evento ya inició, la inscripción no puede cancelarse.");
        }

        if (STATUS_CONFIRMED.equals(registration.getStatus())) {
            event.setAvailableCapacity(event.getAvailableCapacity() + 1);
            eventRepository.saveAndFlush(event);
        }

        registration.setStatus(STATUS_CANCELLED);
        registration.setCancelledAt(now);
        registration.setStatusUpdatedAt(now);

        return registrationMapper.toResponse(registrationRepository.saveAndFlush(registration));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RegistrationResponse> findMine(String status, Pageable pageable) {
        Long participantId = securityUtils.currentUserId();
        String normalizedStatus = normalizeStatus(status);

        Page<Registration> registrations = normalizedStatus == null
                ? registrationRepository.findByParticipantId(participantId, pageable)
                : registrationRepository.findByParticipantIdAndStatus(participantId, normalizedStatus, pageable);

        return registrations.map(registrationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RegistrationResponse> findByEvent(Long eventId, String status, Pageable pageable) {
        Event event = findActiveEventOrThrow(eventId);
        requireEventManager(event);

        String normalizedStatus = normalizeStatus(status);
        Page<Registration> registrations = normalizedStatus == null
                ? registrationRepository.findByEventId(eventId, pageable)
                : registrationRepository.findByEventIdAndStatus(eventId, normalizedStatus, pageable);

        return registrations.map(registrationMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public RegistrationResponse findById(Long id) {
        Registration registration = findRegistrationOrThrow(id);

        boolean isOwner = registration.getParticipant().getId().equals(securityUtils.currentUserId());
        boolean isOrganizer = registration.getEvent().getOrganizerId().equals(securityUtils.currentUserId());

        if (!securityUtils.isAdmin() && !isOwner && !isOrganizer) {
            throw new AccessDeniedException("No tiene acceso a esta inscripción.");
        }

        return registrationMapper.toResponse(registration);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RegistrationResponse> findAll(Long eventId, String status, Pageable pageable) {
        return registrationRepository.search(eventId, normalizeStatus(status), pageable)
                .map(registrationMapper::toResponse);
    }

    private RegistrationResponse applyStatusChange(Long id, RegistrationStatusRequest request) {
        Registration registration = findRegistrationOrThrow(id);
        requireEventManager(registration.getEvent());

        if (!STATUS_PENDING.equals(registration.getStatus())) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Solo las inscripciones en estado PENDING pueden confirmarse o rechazarse.");
        }

        String targetStatus = request.status();
        Instant now = Instant.now();

        if (STATUS_CONFIRMED.equals(targetStatus)) {
            Event event = eventRepository.findById(registration.getEvent().getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            ErrorCode.RESOURCE_NOT_FOUND, "No se encontró el evento de la inscripción."));

            if (!hasAvailableCapacity(event)) {
                throw new NoCapacityException(ErrorCode.NO_CAPACITY,
                        "El evento no tiene cupos disponibles para confirmar la inscripción.");
            }

            event.setAvailableCapacity(event.getAvailableCapacity() - 1);
            eventRepository.saveAndFlush(event);

            registration.setConfirmedAt(now);
        }

        registration.setStatus(targetStatus);
        registration.setStatusUpdatedAt(now);

        return registrationMapper.toResponse(registrationRepository.saveAndFlush(registration));
    }

    private Registration findRegistrationOrThrow(Long id) {
        return registrationRepository.findWithDetailsById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No se encontró la inscripción solicitada."));
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

    private void requireEventManager(Event event) {
        if (!securityUtils.isAdmin() && !event.getOrganizerId().equals(securityUtils.currentUserId())) {
            throw new AccessDeniedException("Solo el organizador del evento puede gestionar sus inscripciones.");
        }
    }

    private boolean hasAvailableCapacity(Event event) {
        return event.getAvailableCapacity() != null && event.getAvailableCapacity() > 0;
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? null : status.trim().toUpperCase();
    }
}
