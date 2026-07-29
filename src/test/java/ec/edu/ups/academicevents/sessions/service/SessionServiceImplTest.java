package ec.edu.ups.academicevents.sessions.service;

import ec.edu.ups.academicevents.events.entity.Event;
import ec.edu.ups.academicevents.events.repository.EventRepository;
import ec.edu.ups.academicevents.sessions.dto.SessionRequest;
import ec.edu.ups.academicevents.sessions.dto.SessionResponse;
import ec.edu.ups.academicevents.sessions.entity.Session;
import ec.edu.ups.academicevents.sessions.mapper.SessionMapper;
import ec.edu.ups.academicevents.sessions.repository.SessionRepository;
import ec.edu.ups.academicevents.shared.exception.BusinessRuleException;
import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SessionServiceImplTest {

    private static final Long EVENT_ID = 1L;
    private static final Long SESSION_ID = 100L;
    private static final Long ORGANIZER_ID = 10L;
    private static final Long OTHER_ORGANIZER_ID = 99L;

    @Mock
    private SessionRepository sessionRepository;
    @Mock
    private EventRepository eventRepository;
    @Spy
    private SessionMapper sessionMapper = new SessionMapper();
    @Mock
    private SecurityUtils securityUtils;

    private SessionServiceImpl sessionService;

    @BeforeEach
    void setUp() {
        sessionService = new SessionServiceImpl(sessionRepository, eventRepository, sessionMapper, securityUtils);
    }

    @Test
    @DisplayName("crear una sesión dentro del rango del evento padre la persiste correctamente")
    void createWithinEventRangeSucceeds() {
        Event event = eventWithRange();
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        given(sessionRepository.existsByEventIdAndTitleAndStartAt(any(), any(), any())).willReturn(false);
        given(sessionRepository.save(any(Session.class))).willAnswer(invocation -> {
            Session session = invocation.getArgument(0);
            session.setId(SESSION_ID);
            return session;
        });

        SessionRequest request = sessionRequest(
                event.getStartAt().plus(1, ChronoUnit.HOURS), event.getStartAt().plus(2, ChronoUnit.HOURS));

        SessionResponse response = sessionService.create(EVENT_ID, request);

        assertThat(response.id()).isEqualTo(SESSION_ID);
        assertThat(response.eventId()).isEqualTo(EVENT_ID);
    }

    @Test
    @DisplayName("crear una sesión fuera del rango del evento padre lanza BusinessRuleException")
    void createOutsideEventRangeFails() {
        Event event = eventWithRange();
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        given(sessionRepository.existsByEventIdAndTitleAndStartAt(any(), any(), any())).willReturn(false);

        SessionRequest request = sessionRequest(
                event.getEndAt().plus(1, ChronoUnit.HOURS), event.getEndAt().plus(2, ChronoUnit.HOURS));

        assertThatThrownBy(() -> sessionService.create(EVENT_ID, request))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(exception -> ((BusinessRuleException) exception).getCode())
                .isEqualTo(ErrorCode.BUSINESS_RULE_VIOLATION);

        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    @DisplayName("actualizar una sesión por un organizador que no es dueño del evento padre lanza AccessDeniedException")
    void updateByNonOwnerOfParentEventFails() {
        Event event = eventWithRange();
        Session session = existingSession(event);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(securityUtils.isAdmin()).willReturn(false);
        given(securityUtils.currentUserId()).willReturn(OTHER_ORGANIZER_ID);

        SessionRequest request = sessionRequest(
                event.getStartAt().plus(1, ChronoUnit.HOURS), event.getStartAt().plus(2, ChronoUnit.HOURS));

        assertThatThrownBy(() -> sessionService.update(SESSION_ID, request))
                .isInstanceOf(AccessDeniedException.class);

        verify(sessionRepository, never()).save(any(Session.class));
    }

    @Test
    @DisplayName("listar las sesiones de un evento las devuelve ordenadas por fecha de inicio")
    void findByEventReturnsSessionsOrderedByStartDate() {
        Event event = eventWithRange();
        Session session = existingSession(event);
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(sessionRepository.findByEventIdOrderByStartAtAsc(EVENT_ID)).willReturn(List.of(session));

        List<SessionResponse> responses = sessionService.findByEvent(EVENT_ID);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).id()).isEqualTo(SESSION_ID);
        verify(sessionRepository).findByEventIdOrderByStartAtAsc(EVENT_ID);
    }

    @Test
    @DisplayName("eliminar una sesión valida la propiedad heredada del evento padre y la borra")
    void deleteValidatesInheritedOwnershipAndDeletes() {
        Event event = eventWithRange();
        Session session = existingSession(event);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);

        sessionService.delete(SESSION_ID);

        verify(sessionRepository).delete(session);
    }

    @Test
    @DisplayName("eliminar una sesión de un organizador ajeno al evento padre lanza AccessDeniedException")
    void deleteByNonOwnerOfParentEventFails() {
        Event event = eventWithRange();
        Session session = existingSession(event);
        given(sessionRepository.findById(SESSION_ID)).willReturn(Optional.of(session));
        given(securityUtils.isAdmin()).willReturn(false);
        given(securityUtils.currentUserId()).willReturn(OTHER_ORGANIZER_ID);

        assertThatThrownBy(() -> sessionService.delete(SESSION_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(sessionRepository, never()).delete(any(Session.class));
    }

    // ---------- datos de prueba ----------

    private Event eventWithRange() {
        return Event.builder()
                .id(EVENT_ID)
                .title("Congreso de Software")
                .status("PUBLISHED")
                .organizerId(ORGANIZER_ID)
                .startAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(12, ChronoUnit.DAYS))
                .deleted(false)
                .build();
    }

    private Session existingSession(Event event) {
        return Session.builder()
                .id(SESSION_ID)
                .event(event)
                .title("Charla de apertura")
                .startAt(event.getStartAt().plus(1, ChronoUnit.HOURS))
                .endAt(event.getStartAt().plus(2, ChronoUnit.HOURS))
                .build();
    }

    private SessionRequest sessionRequest(Instant startAt, Instant endAt) {
        return new SessionRequest("Charla de apertura", "desc", startAt, endAt, null, null);
    }
}
