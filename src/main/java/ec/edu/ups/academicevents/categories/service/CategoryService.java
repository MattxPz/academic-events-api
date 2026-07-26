package ec.edu.ups.academicevents.categories.service;

import ec.edu.ups.academicevents.categories.dto.CategoryRequest;
import ec.edu.ups.academicevents.categories.dto.CategoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    CategoryResponse findById(Long id);

    Page<CategoryResponse> findAll(String q, Boolean includeInactive, Pageable pageable);

    CategoryResponse update(Long id, CategoryRequest request);

    void delete(Long id);
}
