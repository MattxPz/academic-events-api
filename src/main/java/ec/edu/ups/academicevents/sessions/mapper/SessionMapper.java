package ec.edu.ups.academicevents.sessions.mapper;

import ec.edu.ups.academicevents.events.entity.Event;
import ec.edu.ups.academicevents.sessions.dto.SessionRequest;
import ec.edu.ups.academicevents.sessions.dto.SessionResponse;
import ec.edu.ups.academicevents.sessions.entity.Session;
import org.springframework.stereotype.Component;

@Component
public class SessionMapper {

    public SessionResponse toResponse(Session entity) {
        return new SessionResponse(
                entity.getId(),
                entity.getEvent().getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.getLocation(),
                entity.getVirtualUrl(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public Session toEntity(SessionRequest request, Event event) {
        return Session.builder()
                .event(event)
                .title(request.title())
                .description(request.description())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .location(request.location())
                .virtualUrl(request.virtualUrl())
                .build();
    }

    public void updateEntity(Session entity, SessionRequest request) {
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setLocation(request.location());
        entity.setVirtualUrl(request.virtualUrl());
    }
}
