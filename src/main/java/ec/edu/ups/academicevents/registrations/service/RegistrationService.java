package ec.edu.ups.academicevents.registrations.service;

import ec.edu.ups.academicevents.registrations.dto.RegistrationRequest;
import ec.edu.ups.academicevents.registrations.dto.RegistrationResponse;
import ec.edu.ups.academicevents.registrations.dto.RegistrationStatusRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RegistrationService {

    RegistrationResponse create(RegistrationRequest request);

    RegistrationResponse updateStatus(Long id, RegistrationStatusRequest request);

    RegistrationResponse cancel(Long id);

    Page<RegistrationResponse> findMine(String status, Pageable pageable);

    Page<RegistrationResponse> findByEvent(Long eventId, String status, Pageable pageable);

    RegistrationResponse findById(Long id);

    Page<RegistrationResponse> findAll(Long eventId, String status, Pageable pageable);
}
