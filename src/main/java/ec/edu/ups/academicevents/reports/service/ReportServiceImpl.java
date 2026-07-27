package ec.edu.ups.academicevents.reports.service;

import ec.edu.ups.academicevents.reports.dto.CertificateData;
import ec.edu.ups.academicevents.reports.dto.EventReportHeader;
import ec.edu.ups.academicevents.reports.dto.RegistrantRow;
import ec.edu.ups.academicevents.reports.dto.ReportFile;
import ec.edu.ups.academicevents.reports.dto.StatsSummaryResponse;
import ec.edu.ups.academicevents.reports.dto.StatusCount;
import ec.edu.ups.academicevents.reports.exception.InvalidReportRangeException;
import ec.edu.ups.academicevents.reports.generator.ExcelGenerator;
import ec.edu.ups.academicevents.reports.generator.PdfGenerator;
import ec.edu.ups.academicevents.reports.repository.ReportQueryRepository;
import ec.edu.ups.academicevents.shared.exception.BusinessRuleException;
import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.exception.ResourceNotFoundException;
import ec.edu.ups.academicevents.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final String STATUS_CONFIRMED = "CONFIRMED";
    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_ORGANIZER = "ORGANIZER";
    private static final int DEFAULT_RANGE_DAYS = 30;

    private final ReportQueryRepository reportQueryRepository;
    private final PdfGenerator pdfGenerator;
    private final ExcelGenerator excelGenerator;
    private final SecurityUtils securityUtils;

    @Override
    @Transactional(readOnly = true)
    public ReportFile registrationsPdf(Long eventId) {
        EventReportHeader event = findEventOrThrow(eventId);
        requireEventManager(event);

        List<RegistrantRow> registrants = reportQueryRepository.findRegistrants(eventId);

        return new ReportFile(
                "registrations-event-" + eventId + ".pdf",
                pdfGenerator.registrantsPdf(event, registrants));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportFile registrationsXlsx(Long eventId) {
        EventReportHeader event = findEventOrThrow(eventId);
        requireEventManager(event);

        List<RegistrantRow> registrants = reportQueryRepository.findRegistrants(eventId);

        return new ReportFile(
                "registrations-event-" + eventId + ".xlsx",
                excelGenerator.registrantsWorkbook(event, registrants));
    }

    @Override
    @Transactional(readOnly = true)
    public ReportFile certificatePdf(Long registrationId) {
        CertificateData certificate = reportQueryRepository.findCertificateData(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No se encontró la inscripción solicitada."));

        if (!securityUtils.isAdmin() && !certificate.participantId().equals(securityUtils.currentUserId())) {
            throw new AccessDeniedException("El certificado pertenece a otro participante.");
        }

        if (!STATUS_CONFIRMED.equals(certificate.status())) {
            throw new BusinessRuleException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Solo se emite certificado para inscripciones confirmadas.");
        }

        return new ReportFile(
                "certificate-" + certificate.registrationCode() + ".pdf",
                pdfGenerator.certificatePdf(certificate));
    }

    @Override
    @Transactional(readOnly = true)
    public StatsSummaryResponse statsSummary(Instant from, Instant to) {
        Instant effectiveTo = to != null ? to : Instant.now();
        Instant effectiveFrom = from != null ? from : effectiveTo.minus(DEFAULT_RANGE_DAYS, ChronoUnit.DAYS);

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new InvalidReportRangeException(
                    "La fecha inicial del rango no puede ser posterior a la fecha final.");
        }

        // Un ADMIN ve todo el sistema; un organizador solo sus propios eventos.
        Long organizerFilter = securityUtils.isAdmin() ? null : securityUtils.currentUserId();

        List<StatusCount> eventsByStatus =
                reportQueryRepository.countEventsByStatus(effectiveFrom, effectiveTo, organizerFilter);
        List<StatusCount> registrationsByStatus =
                reportQueryRepository.countRegistrationsByStatus(effectiveFrom, effectiveTo, organizerFilter);

        return new StatsSummaryResponse(
                effectiveFrom,
                effectiveTo,
                organizerFilter == null ? SCOPE_ALL : SCOPE_ORGANIZER,
                total(eventsByStatus),
                toMap(eventsByStatus),
                total(registrationsByStatus),
                toMap(registrationsByStatus),
                reportQueryRepository.countUniqueParticipants(effectiveFrom, effectiveTo, organizerFilter),
                round(reportQueryRepository.averageOccupancyPercentage(effectiveFrom, effectiveTo, organizerFilter)));
    }

    private EventReportHeader findEventOrThrow(Long eventId) {
        return reportQueryRepository.findEventHeader(eventId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No se encontró el evento solicitado."));
    }

    private void requireEventManager(EventReportHeader event) {
        if (!securityUtils.isAdmin() && !event.organizerId().equals(securityUtils.currentUserId())) {
            throw new AccessDeniedException("Solo el organizador del evento puede descargar sus reportes.");
        }
    }

    private Map<String, Long> toMap(List<StatusCount> counts) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (StatusCount count : counts) {
            result.put(count.status(), count.total());
        }
        return result;
    }

    private long total(List<StatusCount> counts) {
        return counts.stream().mapToLong(StatusCount::total).sum();
    }

    private double round(double value) {
        return Math.round(value * 100d) / 100d;
    }
}
