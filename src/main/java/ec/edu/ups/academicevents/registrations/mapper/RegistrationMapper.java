package ec.edu.ups.academicevents.registrations.mapper;

import ec.edu.ups.academicevents.events.entity.Event;
import ec.edu.ups.academicevents.registrations.dto.RegistrationResponse;
import ec.edu.ups.academicevents.registrations.entity.Registration;
import ec.edu.ups.academicevents.users.entity.User;
import org.springframework.stereotype.Component;

@Component
public class RegistrationMapper {

    public RegistrationResponse toResponse(Registration registration) {
        Event event = registration.getEvent();
        User participant = registration.getParticipant();

        return new RegistrationResponse(
                registration.getId(),
                registration.getRegistrationCode(),
                event.getId(),
                event.getTitle(),
                participant.getId(),
                fullName(participant),
                participant.getEmail(),
                registration.getStatus(),
                registration.getRegisteredAt(),
                registration.getStatusUpdatedAt(),
                registration.getConfirmedAt(),
                registration.getCancelledAt());
    }

    private String fullName(User participant) {
        return participant.getFirstName() + " " + participant.getLastName();
    }
}
