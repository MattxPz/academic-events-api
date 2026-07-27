package ec.edu.ups.academicevents.registrations.service;

import ec.edu.ups.academicevents.events.entity.Event;
import ec.edu.ups.academicevents.events.repository.EventRepository;
import ec.edu.ups.academicevents.registrations.dto.RegistrationRequest;
import ec.edu.ups.academicevents.registrations.dto.RegistrationResponse;
import ec.edu.ups.academicevents.registrations.dto.RegistrationStatusRequest;
import ec.edu.ups.academicevents.registrations.entity.Registration;
import ec.edu.ups.academicevents.registrations.mapper.RegistrationMapper;
import ec.edu.ups.academicevents.registrations.repository.RegistrationRepository;
import ec.edu.ups.academicevents.shared.audit.AuditService;
import ec.edu.ups.academicevents.shared.exception.BusinessRuleException;
import ec.edu.ups.academicevents.shared.exception.DuplicateResourceException;
import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.exception.NoCapacityException;
import ec.edu.ups.academicevents.shared.security.SecurityUtils;
import ec.edu.ups.academicevents.users.entity.User;
import ec.edu.ups.academicevents.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceImplTest {

    private static final Long PARTICIPANT_ID = 12L;
    private static final Long ORGANIZER_ID = 2L;
    private static final Long EVENT_ID = 1L;
    private static final Long REGISTRATION_ID = 100L;

    @Mock
    private RegistrationRepository registrationRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Spy
    private RegistrationMapper registrationMapper = new RegistrationMapper();
    @Mock
    private SecurityUtils securityUtils;
    @Mock
    private AuditService auditService;
    @Mock
    private PlatformTransactionManager transactionManager;

    @InjectMocks
    private RegistrationServiceImpl registrationService;

    @BeforeEach
    void setUp() {
        // Mockito no ejecuta @PostConstruct; el TransactionTemplate se arma a mano.
        registrationService.initTransactionTemplate();
    }

    // ---------- create ----------

    @Test
    @DisplayName("la creación exitosa deja la inscripción en PENDING sin tocar el cupo")
    void createLeavesRegistrationPendingWithoutConsumingCapacity() {
        Event event = publishedEvent(10);
        given(securityUtils.currentUserId()).willReturn(PARTICIPANT_ID);
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(registrationRepository.existsByEventIdAndParticipantId(EVENT_ID, PARTICIPANT_ID)).willReturn(false);
        given(userRepository.findById(PARTICIPANT_ID)).willReturn(Optional.of(participant()));
        given(registrationRepository.saveAndFlush(any(Registration.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponse response = registrationService.create(new RegistrationRequest(EVENT_ID));

        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.registrationCode()).isNotNull();
        assertThat(response.statusUpdatedAt()).isNotNull();
        assertThat(event.getAvailableCapacity()).isEqualTo(10);
        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    @DisplayName("una inscripción duplicada lanza DuplicateResourceException")
    void createRejectsDuplicatedRegistration() {
        Event event = publishedEvent(10);
        given(securityUtils.currentUserId()).willReturn(PARTICIPANT_ID);
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(registrationRepository.existsByEventIdAndParticipantId(EVENT_ID, PARTICIPANT_ID)).willReturn(true);

        assertThatThrownBy(() -> registrationService.create(new RegistrationRequest(EVENT_ID)))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting(exception -> ((DuplicateResourceException) exception).getCode())
                .isEqualTo(ErrorCode.ALREADY_REGISTERED);
    }

    @Test
    @DisplayName("un evento en estado FINISHED lanza BusinessRuleException")
    void createRejectsFinishedEvent() {
        Event event = publishedEvent(10);
        event.setStatus("FINISHED");
        given(securityUtils.currentUserId()).willReturn(PARTICIPANT_ID);
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        // El estado del evento se valida antes que las fechas, de ahí el código EVENT_NOT_OPEN.
        assertThatThrownBy(() -> registrationService.create(new RegistrationRequest(EVENT_ID)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(exception -> ((BusinessRuleException) exception).getCode())
                .isEqualTo(ErrorCode.EVENT_NOT_OPEN);
    }

    @Test
    @DisplayName("un evento publicado que ya terminó lanza BusinessRuleException con EVENT_FINISHED")
    void createRejectsEventAlreadyEnded() {
        Event event = publishedEvent(10);
        event.setEndAt(Instant.now().minus(1, ChronoUnit.HOURS));
        given(securityUtils.currentUserId()).willReturn(PARTICIPANT_ID);
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        assertThatThrownBy(() -> registrationService.create(new RegistrationRequest(EVENT_ID)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(exception -> ((BusinessRuleException) exception).getCode())
                .isEqualTo(ErrorCode.EVENT_FINISHED);
    }

    @Test
    @DisplayName("un evento fuera del período de inscripción lanza REGISTRATION_WINDOW_CLOSED")
    void createRejectsClosedRegistrationWindow() {
        Event event = publishedEvent(10);
        event.setRegistrationEndAt(Instant.now().minus(1, ChronoUnit.DAYS));
        given(securityUtils.currentUserId()).willReturn(PARTICIPANT_ID);
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        assertThatThrownBy(() -> registrationService.create(new RegistrationRequest(EVENT_ID)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(exception -> ((BusinessRuleException) exception).getCode())
                .isEqualTo(ErrorCode.REGISTRATION_WINDOW_CLOSED);
    }

    @Test
    @DisplayName("un evento sin cupos disponibles lanza NoCapacityException")
    void createRejectsEventWithoutCapacity() {
        Event event = publishedEvent(0);
        given(securityUtils.currentUserId()).willReturn(PARTICIPANT_ID);
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(registrationRepository.existsByEventIdAndParticipantId(EVENT_ID, PARTICIPANT_ID)).willReturn(false);

        assertThatThrownBy(() -> registrationService.create(new RegistrationRequest(EVENT_ID)))
                .isInstanceOf(NoCapacityException.class)
                .extracting(exception -> ((NoCapacityException) exception).getCode())
                .isEqualTo(ErrorCode.NO_CAPACITY);
    }

    // ---------- updateStatus ----------

    @Test
    @DisplayName("confirmar una inscripción PENDING decrementa availableCapacity en 1")
    void confirmDecrementsAvailableCapacity() {
        Event event = publishedEvent(10);
        Registration registration = pendingRegistration(event);
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        given(registrationRepository.findWithDetailsById(REGISTRATION_ID)).willReturn(Optional.of(registration));
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(registrationRepository.saveAndFlush(any(Registration.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponse response =
                registrationService.updateStatus(REGISTRATION_ID, new RegistrationStatusRequest("CONFIRMED"));

        assertThat(response.status()).isEqualTo("CONFIRMED");
        assertThat(response.confirmedAt()).isNotNull();
        assertThat(event.getAvailableCapacity()).isEqualTo(9);
        verify(eventRepository).saveAndFlush(event);
    }

    @Test
    @DisplayName("rechazar una inscripción PENDING no toca el cupo")
    void rejectDoesNotTouchCapacity() {
        Event event = publishedEvent(10);
        Registration registration = pendingRegistration(event);
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        given(registrationRepository.findWithDetailsById(REGISTRATION_ID)).willReturn(Optional.of(registration));
        given(registrationRepository.saveAndFlush(any(Registration.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponse response =
                registrationService.updateStatus(REGISTRATION_ID, new RegistrationStatusRequest("REJECTED"));

        assertThat(response.status()).isEqualTo("REJECTED");
        assertThat(response.confirmedAt()).isNull();
        assertThat(event.getAvailableCapacity()).isEqualTo(10);
        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    @DisplayName("confirmar cuando availableCapacity es 0 lanza NoCapacityException")
    void confirmWithoutCapacityFails() {
        Event event = publishedEvent(0);
        Registration registration = pendingRegistration(event);
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        given(registrationRepository.findWithDetailsById(REGISTRATION_ID)).willReturn(Optional.of(registration));
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        assertThatThrownBy(() ->
                registrationService.updateStatus(REGISTRATION_ID, new RegistrationStatusRequest("CONFIRMED")))
                .isInstanceOf(NoCapacityException.class);

        assertThat(event.getAvailableCapacity()).isZero();
        verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
    }

    @Test
    @DisplayName("un organizador que no es dueño del evento no puede cambiar el estado")
    void updateStatusRejectsForeignOrganizer() {
        Event event = publishedEvent(10);
        Registration registration = pendingRegistration(event);
        given(securityUtils.isAdmin()).willReturn(false);
        given(securityUtils.currentUserId()).willReturn(999L);
        given(registrationRepository.findWithDetailsById(REGISTRATION_ID)).willReturn(Optional.of(registration));

        assertThatThrownBy(() ->
                registrationService.updateStatus(REGISTRATION_ID, new RegistrationStatusRequest("CONFIRMED")))
                .isInstanceOf(AccessDeniedException.class);

        assertThat(event.getAvailableCapacity()).isEqualTo(10);
        verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
    }

    @Test
    @DisplayName("una transición inválida (CANCELLED -> CONFIRMED) lanza BusinessRuleException")
    void updateStatusRejectsInvalidTransition() {
        Event event = publishedEvent(10);
        Registration registration = pendingRegistration(event);
        registration.setStatus("CANCELLED");
        registration.setCancelledAt(Instant.now());
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        given(registrationRepository.findWithDetailsById(REGISTRATION_ID)).willReturn(Optional.of(registration));

        assertThatThrownBy(() ->
                registrationService.updateStatus(REGISTRATION_ID, new RegistrationStatusRequest("CONFIRMED")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(exception -> ((BusinessRuleException) exception).getCode())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    // ---------- cancel ----------

    @Test
    @DisplayName("cancelar una inscripción CONFIRMED devuelve el cupo")
    void cancelConfirmedGivesCapacityBack() {
        Event event = publishedEvent(9);
        Registration registration = pendingRegistration(event);
        registration.setStatus("CONFIRMED");
        registration.setConfirmedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        given(securityUtils.currentUserId()).willReturn(PARTICIPANT_ID);
        given(registrationRepository.findWithDetailsById(REGISTRATION_ID)).willReturn(Optional.of(registration));
        given(registrationRepository.saveAndFlush(any(Registration.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponse response = registrationService.cancel(REGISTRATION_ID);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(response.cancelledAt()).isNotNull();
        assertThat(event.getAvailableCapacity()).isEqualTo(10);
        verify(eventRepository).saveAndFlush(event);
    }

    @Test
    @DisplayName("cancelar una inscripción PENDING no modifica el cupo")
    void cancelPendingKeepsCapacity() {
        Event event = publishedEvent(10);
        Registration registration = pendingRegistration(event);
        given(securityUtils.currentUserId()).willReturn(PARTICIPANT_ID);
        given(registrationRepository.findWithDetailsById(REGISTRATION_ID)).willReturn(Optional.of(registration));
        given(registrationRepository.saveAndFlush(any(Registration.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        RegistrationResponse response = registrationService.cancel(REGISTRATION_ID);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(event.getAvailableCapacity()).isEqualTo(10);
        verify(eventRepository, never()).saveAndFlush(any(Event.class));
    }

    @Test
    @DisplayName("un participante ajeno no puede cancelar la inscripción")
    void cancelRejectsForeignParticipant() {
        Event event = publishedEvent(10);
        Registration registration = pendingRegistration(event);
        given(securityUtils.isAdmin()).willReturn(false);
        given(securityUtils.currentUserId()).willReturn(777L);
        given(registrationRepository.findWithDetailsById(REGISTRATION_ID)).willReturn(Optional.of(registration));

        assertThatThrownBy(() -> registrationService.cancel(REGISTRATION_ID))
                .isInstanceOf(AccessDeniedException.class);

        verify(registrationRepository, never()).saveAndFlush(any(Registration.class));
    }

    // ---------- datos de prueba ----------

    private Event publishedEvent(int availableCapacity) {
        return Event.builder()
                .id(EVENT_ID)
                .title("Jornada de Ingeniería de Software")
                .status("PUBLISHED")
                .capacity(40)
                .availableCapacity(availableCapacity)
                .registrationStartAt(Instant.now().minus(10, ChronoUnit.DAYS))
                .registrationEndAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .startAt(Instant.now().plus(20, ChronoUnit.DAYS))
                .endAt(Instant.now().plus(21, ChronoUnit.DAYS))
                .organizerId(ORGANIZER_ID)
                .deleted(false)
                .version(0L)
                .build();
    }

    private Registration pendingRegistration(Event event) {
        return Registration.builder()
                .id(REGISTRATION_ID)
                .registrationCode(UUID.randomUUID())
                .event(event)
                .participant(participant())
                .status("PENDING")
                .statusUpdatedAt(Instant.now().minus(2, ChronoUnit.DAYS))
                .version(0L)
                .build();
    }

    private User participant() {
        return User.builder()
                .id(PARTICIPANT_ID)
                .firstName("Paula")
                .lastName("Castillo")
                .email("paula.castillo@academic.test")
                .status("ACTIVE")
                .build();
    }
}
