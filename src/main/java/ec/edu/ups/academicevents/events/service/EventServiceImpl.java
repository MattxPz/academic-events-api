package ec.edu.ups.academicevents.events.service;

import ec.edu.ups.academicevents.categories.entity.Category;
import ec.edu.ups.academicevents.categories.repository.CategoryRepository;
import ec.edu.ups.academicevents.events.dto.EventRequest;
import ec.edu.ups.academicevents.events.dto.EventResponse;
import ec.edu.ups.academicevents.events.dto.EventStatusRequest;
import ec.edu.ups.academicevents.events.entity.Event;
import ec.edu.ups.academicevents.events.mapper.EventMapper;
import ec.edu.ups.academicevents.events.repository.EventRepository;
import ec.edu.ups.academicevents.registrations.repository.RegistrationRepository;
import ec.edu.ups.academicevents.shared.exception.BusinessRuleException;
import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.exception.ResourceNotFoundException;
import ec.edu.ups.academicevents.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private static final Map<String, Set<String>> ALLOWED_STATUS_TRANSITIONS = Map.of(
            "DRAFT", Set.of("PUBLISHED", "CANCELLED"),
            "PUBLISHED", Set.of("FINISHED", "CANCELLED"));

    private final EventRepository eventRepository;
    private final CategoryRepository categoryRepository;
    private final RegistrationRepository registrationRepository;
    private final EventMapper eventMapper;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional
    public EventResponse create(EventRequest request) {
        Long organizerId = securityUtils.currentUserId();
        Category category = findActiveCategoryOrThrow(request.categoryId());

        Event event = eventMapper.toEntity(request, category, organizerId);
        event = eventRepository.save(event);
        return eventMapper.toResponse(event);
    }

    @Override
    @Transactional(readOnly = true)
    public EventResponse findById(Long id) {
        return eventMapper.toResponse(findActiveEventOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> findAll(
            String q, Long categoryId, String modality, String status, Instant from, Instant to,
            Pageable pageable) {
        Specification<Event> specification = (root, query, cb) -> cb.conjunction();

        specification = specification.and((root, query, cb) -> cb.isFalse(root.get("deleted")));

        if (q != null && !q.isBlank()) {
            String likePattern = "%" + q.trim().toLowerCase() + "%";
            specification = specification.and(
                    (root, query, cb) -> cb.like(cb.lower(root.get("title")), cb.lower(cb.literal(likePattern))));
        }

        if (categoryId != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId));
        }

        if (modality != null && !modality.isBlank()) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("modality"), modality));
        }

        if (status != null && !status.isBlank()) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        if (from != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("startAt"), from));
        }

        if (to != null) {
            specification = specification.and(
                    (root, query, cb) -> cb.lessThanOrEqualTo(root.get("startAt"), to));
        }

        return eventRepository.findAll(specification, pageable).map(eventMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<EventResponse> findMine(Pageable pageable) {
        Long organizerId = securityUtils.currentUserId();

        Specification<Event> specification = (root, query, cb) -> cb.conjunction();
        specification = specification.and((root, query, cb) -> cb.isFalse(root.get("deleted")));
        specification = specification.and(
                (root, query, cb) -> cb.equal(root.get("organizerId"), organizerId));

        return eventRepository.findAll(specification, pageable).map(eventMapper::toResponse);
    }

    @Override
    @Transactional
    public EventResponse update(Long id, EventRequest request) {
        Event event = findActiveEventOrThrow(id);
        requireEventOwner(event);

        Category category = findActiveCategoryOrThrow(request.categoryId());
        eventMapper.updateEntity(event, request, category);

        event = eventRepository.save(event);
        return eventMapper.toResponse(event);
    }

    @Override
    @Transactional
    public EventResponse changeStatus(Long id, EventStatusRequest request) {
        Event event = findActiveEventOrThrow(id);
        requireEventOwner(event);

        String currentStatus = event.getStatus();
        String targetStatus = request.status();

        Set<String> allowedTargets = ALLOWED_STATUS_TRANSITIONS.getOrDefault(currentStatus, Set.of());
        if (!allowedTargets.contains(targetStatus)) {
            throw new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                    "No se puede cambiar el evento de " + currentStatus + " a " + targetStatus + ".");
        }

        event.setStatus(targetStatus);
        event = eventRepository.save(event);
        return eventMapper.toResponse(event);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Event event = findEventOrThrow(id);
        requireEventOwner(event);

        if ("PUBLISHED".equals(event.getStatus())
                && registrationRepository.countByEventIdAndStatus(id, "CONFIRMED") > 0) {
            throw new BusinessRuleException(ErrorCode.EVENT_HAS_REGISTRATIONS,
                    "No se puede eliminar un evento publicado que tiene inscripciones confirmadas.");
        }

        if (!Boolean.TRUE.equals(event.getDeleted())) {
            event.setDeleted(true);
            eventRepository.save(event);
        }
    }

    private Event findEventOrThrow(Long id) {
        return eventRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No se encontró el evento solicitado."));
    }

    private Event findActiveEventOrThrow(Long id) {
        Event event = findEventOrThrow(id);

        if (Boolean.TRUE.equals(event.getDeleted())) {
            throw new ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND, "No se encontró el evento solicitado.");
        }

        return event;
    }

    private Category findActiveCategoryOrThrow(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No se encontró la categoría solicitada."));

        if (!Boolean.TRUE.equals(category.getActive())) {
            throw new ResourceNotFoundException(
                    ErrorCode.RESOURCE_NOT_FOUND, "La categoría solicitada no está activa.");
        }

        return category;
    }

    private void requireEventOwner(Event event) {
        if (!securityUtils.isAdmin() && !event.getOrganizerId().equals(securityUtils.currentUserId())) {
            throw new AccessDeniedException("Solo el organizador del evento puede gestionarlo.");
        }
    }
}
