package ec.edu.ups.academicevents.categories.mapper;

import ec.edu.ups.academicevents.categories.dto.CategoryRequest;
import ec.edu.ups.academicevents.categories.dto.CategoryResponse;
import ec.edu.ups.academicevents.categories.entity.Category;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category entity) {
        return new CategoryResponse(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public Category toEntity(CategoryRequest request) {
        return Category.builder()
                .name(request.name())
                .description(request.description())
                .active(true)
                .build();
    }
}
