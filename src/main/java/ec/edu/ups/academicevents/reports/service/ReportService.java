package ec.edu.ups.academicevents.reports.service;

import ec.edu.ups.academicevents.reports.dto.ReportFile;
import ec.edu.ups.academicevents.reports.dto.StatsSummaryResponse;

import java.time.Instant;

public interface ReportService {

    ReportFile registrationsPdf(Long eventId);

    ReportFile registrationsXlsx(Long eventId);

    ReportFile certificatePdf(Long registrationId);

    StatsSummaryResponse statsSummary(Instant from, Instant to);
}
