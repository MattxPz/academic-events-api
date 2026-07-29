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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    private static final Long EVENT_ID = 1L;
    private static final Long CATEGORY_ID = 5L;
    private static final Long ORGANIZER_ID = 10L;
    private static final Long OTHER_ORGANIZER_ID = 99L;

    @Mock
    private EventRepository eventRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private RegistrationRepository registrationRepository;
    @Spy
    private EventMapper eventMapper = new EventMapper();
    @Mock
    private SecurityUtils securityUtils;

    private EventServiceImpl eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventServiceImpl(
                eventRepository, categoryRepository, registrationRepository, eventMapper, securityUtils);
    }

    // ---------- create ----------

    @Test
    @DisplayName("crear un evento fija availableCapacity = capacity")
    void createSetsAvailableCapacityEqualToCapacity() {
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        given(categoryRepository.findById(CATEGORY_ID)).willReturn(Optional.of(activeCategory()));
        given(eventRepository.save(any(Event.class))).willAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            event.setId(EVENT_ID);
            return event;
        });

        EventResponse response = eventService.create(eventRequest(40));

        assertThat(response.capacity()).isEqualTo(40);
        assertThat(response.availableCapacity()).isEqualTo(40);
        assertThat(response.status()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("crear con categoryId inexistente lanza ResourceNotFoundException")
    void createWithNonExistentCategoryFails() {
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        given(categoryRepository.findById(CATEGORY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.create(eventRequest(40)))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(exception -> ((ResourceNotFoundException) exception).getCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);

        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("crear con una categoría inactiva lanza ResourceNotFoundException")
    void createWithInactiveCategoryFails() {
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        Category inactive = activeCategory();
        inactive.setActive(false);
        given(categoryRepository.findById(CATEGORY_ID)).willReturn(Optional.of(inactive));

        assertThatThrownBy(() -> eventService.create(eventRequest(40)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(eventRepository, never()).save(any(Event.class));
    }

    // ---------- update ----------

    @Test
    @DisplayName("actualizar un evento cuyo organizador no es el dueño lanza AccessDeniedException")
    void updateByNonOwnerOrganizerFails() {
        Event event = draftEvent();
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(securityUtils.isAdmin()).willReturn(false);
        given(securityUtils.currentUserId()).willReturn(OTHER_ORGANIZER_ID);

        assertThatThrownBy(() -> eventService.update(EVENT_ID, eventRequest(40)))
                .isInstanceOf(AccessDeniedException.class);

        verify(eventRepository, never()).save(any(Event.class));
    }

    @Test
    @DisplayName("actualizar un evento inexistente lanza ResourceNotFoundException")
    void updateNonExistentEventFails() {
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> eventService.update(EVENT_ID, eventRequest(40)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- changeStatus ----------

    @Test
    @DisplayName("la transición DRAFT -> PUBLISHED es válida")
    void validTransitionFromDraftToPublished() {
        Event event = draftEvent();
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        given(eventRepository.save(any(Event.class))).willAnswer(invocation -> invocation.getArgument(0));

        EventResponse response = eventService.changeStatus(EVENT_ID, new EventStatusRequest("PUBLISHED"));

        assertThat(response.status()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("una transición inválida (DRAFT -> FINISHED) lanza BusinessRuleException")
    void invalidTransitionFails() {
        Event event = draftEvent();
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);

        assertThatThrownBy(() -> eventService.changeStatus(EVENT_ID, new EventStatusRequest("FINISHED")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(exception -> ((BusinessRuleException) exception).getCode())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);

        verify(eventRepository, never()).save(any(Event.class));
    }

    // ---------- delete ----------

    @Test
    @DisplayName("borrar un evento DRAFT sin inscripciones hace el borrado lógico exitosamente")
    void deleteDraftEventWithoutRegistrationsSucceeds() {
        Event event = draftEvent();
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        given(eventRepository.save(any(Event.class))).willAnswer(invocation -> invocation.getArgument(0));

        eventService.delete(EVENT_ID);

        assertThat(event.getDeleted()).isTrue();
        verify(eventRepository).save(event);
        verify(registrationRepository, never()).countByEventIdAndStatus(any(), any());
    }

    @Test
    @DisplayName("borrar un evento PUBLISHED con inscripciones CONFIRMED lanza BusinessRuleException y no borra la fila")
    void deletePublishedEventWithConfirmedRegistrationsFails() {
        Event event = draftEvent();
        event.setStatus("PUBLISHED");
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        given(registrationRepository.countByEventIdAndStatus(EVENT_ID, "CONFIRMED")).willReturn(3L);

        assertThatThrownBy(() -> eventService.delete(EVENT_ID))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(exception -> ((BusinessRuleException) exception).getCode())
                .isEqualTo(ErrorCode.EVENT_HAS_REGISTRATIONS);

        assertThat(event.getDeleted()).isFalse();
        verify(eventRepository, never()).save(any(Event.class));
    }

    // ---------- findAll ----------

    @Test
    @DisplayName("listar eventos aplica el filtro de categoría y modalidad y respeta el Pageable")
    void findAllAppliesCategoryAndModalityFilters() {
        Event event = draftEvent();
        event.setStatus("PUBLISHED");
        Pageable pageable = PageRequest.of(0, 10);
        Page<Event> page = new PageImpl<>(List.of(event), pageable, 1);

        given(eventRepository.findAll(this.<Event>anySpecification(), eq(pageable))).willReturn(page);

        Page<EventResponse> response = eventService.findAll(
                null, CATEGORY_ID, "VIRTUAL", null, null, null, pageable);

        assertThat(response.getTotalElements()).isEqualTo(1);
        verify(eventRepository).findAll(this.<Event>anySpecification(), eq(pageable));
    }

    @Test
    @DisplayName("un listado público filtrado por status=PUBLISHED no incluye eventos DRAFT")
    void publicListingFilteredByPublishedStatusExcludesDrafts() {
        // El servicio no distingue anónimos de autenticados: la exclusión de DRAFT depende
        // de que el llamador (el endpoint público) envíe explícitamente status=PUBLISHED.
        Event publishedEvent = draftEvent();
        publishedEvent.setStatus("PUBLISHED");
        Pageable pageable = PageRequest.of(0, 10);
        Page<Event> page = new PageImpl<>(List.of(publishedEvent), pageable, 1);

        given(eventRepository.findAll(this.<Event>anySpecification(), eq(pageable))).willReturn(page);

        Page<EventResponse> response = eventService.findAll(
                null, null, null, "PUBLISHED", null, null, pageable);

        assertThat(response.getContent()).extracting(EventResponse::status).containsOnly("PUBLISHED");
    }

    // ---------- datos de prueba ----------

    private Category activeCategory() {
        return Category.builder().id(CATEGORY_ID).name("Tecnología").description("desc").active(true).build();
    }

    private Event draftEvent() {
        return Event.builder()
                .id(EVENT_ID)
                .title("Congreso de Software")
                .description("desc")
                .modality("VIRTUAL")
                .capacity(40)
                .availableCapacity(40)
                .registrationStartAt(Instant.now().minus(5, ChronoUnit.DAYS))
                .registrationEndAt(Instant.now().plus(5, ChronoUnit.DAYS))
                .startAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(11, ChronoUnit.DAYS))
                .status("DRAFT")
                .organizerId(ORGANIZER_ID)
                .category(activeCategory())
                .deleted(false)
                .version(0L)
                .build();
    }

    private EventRequest eventRequest(int capacity) {
        return new EventRequest(
                "Congreso de Software",
                "desc",
                "VIRTUAL",
                null,
                "https://meet.example.com/congreso",
                capacity,
                Instant.now().minus(5, ChronoUnit.DAYS),
                Instant.now().plus(5, ChronoUnit.DAYS),
                Instant.now().plus(10, ChronoUnit.DAYS),
                Instant.now().plus(11, ChronoUnit.DAYS),
                CATEGORY_ID);
    }

    @SuppressWarnings("unchecked")
    private <T> Specification<T> anySpecification() {
        return any(Specification.class);
    }
}
