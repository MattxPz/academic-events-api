package ec.edu.ups.academicevents.categories.service;

import ec.edu.ups.academicevents.categories.dto.CategoryRequest;
import ec.edu.ups.academicevents.categories.dto.CategoryResponse;
import ec.edu.ups.academicevents.categories.entity.Category;
import ec.edu.ups.academicevents.categories.mapper.CategoryMapper;
import ec.edu.ups.academicevents.categories.repository.CategoryRepository;
import ec.edu.ups.academicevents.shared.audit.AuditPayloads;
import ec.edu.ups.academicevents.shared.audit.AuditService;
import ec.edu.ups.academicevents.shared.exception.DuplicateResourceException;
import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.exception.ResourceNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private static final String AUDIT_RESOURCE_CATEGORY = "CATEGORY";
    private static final String AUDIT_CATEGORY_CREATED = "CATEGORY_CREATED";
    private static final String AUDIT_CATEGORY_UPDATED = "CATEGORY_UPDATED";
    private static final String AUDIT_CATEGORY_DEACTIVATED = "CATEGORY_DEACTIVATED";

    private static final ObjectMapper AUDIT_MAPPER = new ObjectMapper();

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;
    private final AuditService auditService;

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException(
                    ErrorCode.DUPLICATE_RESOURCE, "Ya existe una categoría con ese nombre.");
        }

        Category category = categoryMapper.toEntity(request);
        category = categoryRepository.save(category);

        auditService.recordSuccess(AUDIT_CATEGORY_CREATED, AUDIT_RESOURCE_CATEGORY, category.getId(),
                null, categorySnapshot(category));

        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse findById(Long id) {
        return categoryMapper.toResponse(findCategoryOrThrow(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CategoryResponse> findAll(String q, Boolean includeInactive, Pageable pageable) {
        Specification<Category> specification = (root, query, cb) -> cb.conjunction();

        if (includeInactive == null || !includeInactive) {
            specification = specification.and(
                    (root, query, cb) -> cb.isTrue(root.get("active")));
        }

        if (q != null && !q.isBlank()) {
            String likePattern = "%" + q.trim().toLowerCase() + "%";
            specification = specification.and(
                    (root, query, cb) -> cb.like(cb.lower(root.get("name")), cb.lower(cb.literal(likePattern))));
        }

        return categoryRepository.findAll(specification, pageable).map(categoryMapper::toResponse);
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = findCategoryOrThrow(id);
        String previousValue = categorySnapshot(category);

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new DuplicateResourceException(
                    ErrorCode.DUPLICATE_RESOURCE, "Ya existe una categoría con ese nombre.");
        }

        category.setName(request.name());
        category.setDescription(request.description());
        category = categoryRepository.save(category);

        auditService.recordSuccess(AUDIT_CATEGORY_UPDATED, AUDIT_RESOURCE_CATEGORY, category.getId(),
                previousValue, categorySnapshot(category));

        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = findCategoryOrThrow(id);

        if (Boolean.TRUE.equals(category.getActive())) {
            category.setActive(false);
            categoryRepository.save(category);

            auditService.recordSuccess(AUDIT_CATEGORY_DEACTIVATED, AUDIT_RESOURCE_CATEGORY, category.getId(),
                    AuditPayloads.of("active", true), AuditPayloads.of("active", false));
        }
    }

    private Category findCategoryOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No se encontró la categoría solicitada."));
    }

    private String categorySnapshot(Category category) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", category.getName());
        payload.put("description", category.getDescription());

        try {
            return AUDIT_MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("No se pudo serializar el detalle de auditoría.", ex);
        }
    }
}
