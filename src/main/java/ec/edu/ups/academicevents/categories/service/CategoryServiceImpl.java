package ec.edu.ups.academicevents.categories.service;

import ec.edu.ups.academicevents.categories.dto.CategoryRequest;
import ec.edu.ups.academicevents.categories.dto.CategoryResponse;
import ec.edu.ups.academicevents.categories.entity.Category;
import ec.edu.ups.academicevents.categories.mapper.CategoryMapper;
import ec.edu.ups.academicevents.categories.repository.CategoryRepository;
import ec.edu.ups.academicevents.shared.exception.DuplicateResourceException;
import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByNameIgnoreCase(request.name())) {
            throw new DuplicateResourceException(
                    ErrorCode.DUPLICATE_RESOURCE, "Ya existe una categoría con ese nombre.");
        }

        Category category = categoryMapper.toEntity(request);
        category = categoryRepository.save(category);
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

        if (categoryRepository.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw new DuplicateResourceException(
                    ErrorCode.DUPLICATE_RESOURCE, "Ya existe una categoría con ese nombre.");
        }

        category.setName(request.name());
        category.setDescription(request.description());
        category = categoryRepository.save(category);
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category category = findCategoryOrThrow(id);

        if (Boolean.TRUE.equals(category.getActive())) {
            category.setActive(false);
            categoryRepository.save(category);
        }
    }

    private Category findCategoryOrThrow(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No se encontró la categoría solicitada."));
    }
}
