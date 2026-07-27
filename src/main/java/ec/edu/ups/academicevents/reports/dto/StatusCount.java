package ec.edu.ups.academicevents.reports.dto;

/** Proyección de un {@code group by status}. */
public record StatusCount(String status, Long total) {
}
