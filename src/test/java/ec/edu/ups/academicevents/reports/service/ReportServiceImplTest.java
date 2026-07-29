package ec.edu.ups.academicevents.reports.service;

import ec.edu.ups.academicevents.reports.dto.CertificateData;
import ec.edu.ups.academicevents.reports.dto.EventReportHeader;
import ec.edu.ups.academicevents.reports.dto.ReportFile;
import ec.edu.ups.academicevents.reports.exception.InvalidReportRangeException;
import ec.edu.ups.academicevents.reports.generator.ExcelGenerator;
import ec.edu.ups.academicevents.reports.generator.PdfGenerator;
import ec.edu.ups.academicevents.reports.repository.ReportQueryRepository;
import ec.edu.ups.academicevents.shared.exception.BusinessRuleException;
import ec.edu.ups.academicevents.shared.security.SecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    private static final Long EVENT_ID = 1L;
    private static final Long ORGANIZER_ID = 10L;
    private static final Long OTHER_ORGANIZER_ID = 99L;
    private static final Long REGISTRATION_ID = 200L;
    private static final Long PARTICIPANT_ID = 20L;
    private static final Long OTHER_PARTICIPANT_ID = 21L;

    @Mock
    private ReportQueryRepository reportQueryRepository;
    @Mock
    private PdfGenerator pdfGenerator;
    @Mock
    private ExcelGenerator excelGenerator;
    @Mock
    private SecurityUtils securityUtils;

    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(reportQueryRepository, pdfGenerator, excelGenerator, securityUtils);
    }

    @Test
    @DisplayName("el PDF de un evento propio devuelve bytes no vacíos")
    void ownEventPdfReturnsNonEmptyBytes() {
        given(reportQueryRepository.findEventHeader(EVENT_ID)).willReturn(Optional.of(eventHeader(ORGANIZER_ID)));
        given(securityUtils.isAdmin()).willReturn(false);
        given(securityUtils.currentUserId()).willReturn(ORGANIZER_ID);
        given(reportQueryRepository.findRegistrants(EVENT_ID)).willReturn(List.of());
        given(pdfGenerator.registrantsPdf(any(), any())).willReturn(new byte[]{1, 2, 3});

        ReportFile file = reportService.registrationsPdf(EVENT_ID);

        assertThat(file.content()).isNotEmpty();
        assertThat(file.filename()).contains(String.valueOf(EVENT_ID));
    }

    @Test
    @DisplayName("el PDF de un evento ajeno lanza AccessDeniedException")
    void foreignEventPdfFails() {
        given(reportQueryRepository.findEventHeader(EVENT_ID)).willReturn(Optional.of(eventHeader(ORGANIZER_ID)));
        given(securityUtils.isAdmin()).willReturn(false);
        given(securityUtils.currentUserId()).willReturn(OTHER_ORGANIZER_ID);

        assertThatThrownBy(() -> reportService.registrationsPdf(EVENT_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("el certificado de una inscripción CONFIRMED propia se emite correctamente")
    void ownConfirmedRegistrationCertificateSucceeds() {
        given(reportQueryRepository.findCertificateData(REGISTRATION_ID))
                .willReturn(Optional.of(certificateData("CONFIRMED", PARTICIPANT_ID)));
        given(securityUtils.isAdmin()).willReturn(false);
        given(securityUtils.currentUserId()).willReturn(PARTICIPANT_ID);
        given(pdfGenerator.certificatePdf(any())).willReturn(new byte[]{1, 2, 3});

        ReportFile file = reportService.certificatePdf(REGISTRATION_ID);

        assertThat(file.content()).isNotEmpty();
    }

    @Test
    @DisplayName("el certificado de una inscripción PENDING lanza BusinessRuleException")
    void pendingRegistrationCertificateFails() {
        given(reportQueryRepository.findCertificateData(REGISTRATION_ID))
                .willReturn(Optional.of(certificateData("PENDING", PARTICIPANT_ID)));
        given(securityUtils.isAdmin()).willReturn(false);
        given(securityUtils.currentUserId()).willReturn(PARTICIPANT_ID);

        assertThatThrownBy(() -> reportService.certificatePdf(REGISTRATION_ID))
                .isInstanceOf(BusinessRuleException.class);
    }

    @Test
    @DisplayName("el certificado de otro participante lanza AccessDeniedException")
    void certificateOfOtherParticipantFails() {
        given(reportQueryRepository.findCertificateData(REGISTRATION_ID))
                .willReturn(Optional.of(certificateData("CONFIRMED", PARTICIPANT_ID)));
        given(securityUtils.isAdmin()).willReturn(false);
        given(securityUtils.currentUserId()).willReturn(OTHER_PARTICIPANT_ID);

        assertThatThrownBy(() -> reportService.certificatePdf(REGISTRATION_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("las estadísticas con from > to fallan la validación del rango")
    void statsSummaryWithFromAfterToFailsValidation() {
        Instant to = Instant.now();
        Instant from = to.plus(1, ChronoUnit.DAYS);

        assertThatThrownBy(() -> reportService.statsSummary(from, to))
                .isInstanceOf(InvalidReportRangeException.class);
    }

    private EventReportHeader eventHeader(Long organizerId) {
        return new EventReportHeader(
                EVENT_ID, "Congreso de Software", "VIRTUAL",
                Instant.now().plus(1, ChronoUnit.DAYS), Instant.now().plus(2, ChronoUnit.DAYS),
                40, 30, organizerId, "Ana", "Pérez");
    }

    private CertificateData certificateData(String status, Long participantId) {
        return new CertificateData(
                REGISTRATION_ID, UUID.randomUUID(), status,
                participantId, "Paula", "Castillo", "paula.castillo@academic.test",
                "Congreso de Software", "VIRTUAL",
                Instant.now().minus(1, ChronoUnit.DAYS), Instant.now());
    }
}
