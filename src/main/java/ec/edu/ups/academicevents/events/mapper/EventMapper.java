package ec.edu.ups.academicevents.events.mapper;

import ec.edu.ups.academicevents.categories.entity.Category;
import ec.edu.ups.academicevents.events.dto.EventRequest;
import ec.edu.ups.academicevents.events.dto.EventResponse;
import ec.edu.ups.academicevents.events.entity.Event;
import org.springframework.stereotype.Component;

@Component
public class EventMapper {

    public EventResponse toResponse(Event entity) {
        Category category = entity.getCategory();

        return new EventResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getModality(),
                entity.getLocation(),
                entity.getVirtualUrl(),
                entity.getCapacity(),
                entity.getAvailableCapacity(),
                entity.getRegistrationStartAt(),
                entity.getRegistrationEndAt(),
                entity.getStartAt(),
                entity.getEndAt(),
                entity.getStatus(),
                entity.getOrganizerId(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public Event toEntity(EventRequest request, Category category, Long organizerId) {
        return Event.builder()
                .title(request.title())
                .description(request.description())
                .modality(request.modality())
                .location(request.location())
                .virtualUrl(request.virtualUrl())
                .capacity(request.capacity())
                .availableCapacity(request.capacity())
                .registrationStartAt(request.registrationStartAt())
                .registrationEndAt(request.registrationEndAt())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .status("DRAFT")
                .organizerId(organizerId)
                .category(category)
                .deleted(false)
                .build();
    }

    public void updateEntity(Event entity, EventRequest request, Category category) {
        entity.setTitle(request.title());
        entity.setDescription(request.description());
        entity.setModality(request.modality());
        entity.setLocation(request.location());
        entity.setVirtualUrl(request.virtualUrl());
        entity.setCapacity(request.capacity());
        entity.setRegistrationStartAt(request.registrationStartAt());
        entity.setRegistrationEndAt(request.registrationEndAt());
        entity.setStartAt(request.startAt());
        entity.setEndAt(request.endAt());
        entity.setCategory(category);
    }
}
