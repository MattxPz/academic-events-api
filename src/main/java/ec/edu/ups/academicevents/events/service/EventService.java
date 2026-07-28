package ec.edu.ups.academicevents.events.service;

import ec.edu.ups.academicevents.events.dto.EventRequest;
import ec.edu.ups.academicevents.events.dto.EventResponse;
import ec.edu.ups.academicevents.events.dto.EventStatusRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;

public interface EventService {

    EventResponse create(EventRequest request);

    EventResponse findById(Long id);

    Page<EventResponse> findAll(
            String q, Long categoryId, String modality, String status, Instant from, Instant to, Pageable pageable);

    EventResponse update(Long id, EventRequest request);

    EventResponse changeStatus(Long id, EventStatusRequest request);

    void delete(Long id);
}
