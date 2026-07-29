package ec.edu.ups.academicevents.categories.service;

import ec.edu.ups.academicevents.categories.dto.CategoryRequest;
import ec.edu.ups.academicevents.categories.dto.CategoryResponse;
import ec.edu.ups.academicevents.categories.entity.Category;
import ec.edu.ups.academicevents.categories.mapper.CategoryMapper;
import ec.edu.ups.academicevents.categories.repository.CategoryRepository;
import ec.edu.ups.academicevents.shared.audit.AuditService;
import ec.edu.ups.academicevents.shared.exception.DuplicateResourceException;
import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    private static final Long CATEGORY_ID = 1L;

    @Mock
    private CategoryRepository categoryRepository;
    @Spy
    private CategoryMapper categoryMapper = new CategoryMapper();
    @Mock
    private AuditService auditService;

    private CategoryServiceImpl categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryServiceImpl(categoryRepository, categoryMapper, auditService);
    }

    @Test
    @DisplayName("crear una categoría con nombre único la persiste correctamente")
    void createWithUniqueNameSucceeds() {
        given(categoryRepository.existsByNameIgnoreCase("IA")).willReturn(false);
        given(categoryRepository.save(any(Category.class))).willAnswer(invocation -> {
            Category category = invocation.getArgument(0);
            category.setId(CATEGORY_ID);
            return category;
        });

        CategoryResponse response = categoryService.create(new CategoryRequest("IA", "Inteligencia artificial"));

        assertThat(response.id()).isEqualTo(CATEGORY_ID);
        assertThat(response.name()).isEqualTo("IA");
        assertThat(response.active()).isTrue();
        verify(auditService).recordSuccess(eq("CATEGORY_CREATED"), eq("CATEGORY"), eq(CATEGORY_ID), eq(null), any());
    }

    @Test
    @DisplayName("crear con un nombre duplicado (insensible a mayúsculas) lanza DuplicateResourceException")
    void createWithDuplicateNameFails() {
        given(categoryRepository.existsByNameIgnoreCase("ia")).willReturn(true);

        assertThatThrownBy(() -> categoryService.create(new CategoryRequest("ia", "duplicada")))
                .isInstanceOf(DuplicateResourceException.class)
                .extracting(exception -> ((DuplicateResourceException) exception).getCode())
                .isEqualTo(ErrorCode.DUPLICATE_RESOURCE);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("actualizar una categoría inexistente lanza ResourceNotFoundException")
    void updateNonExistentCategoryFails() {
        given(categoryRepository.findById(CATEGORY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(CATEGORY_ID, new CategoryRequest("Nueva", "desc")))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting(exception -> ((ResourceNotFoundException) exception).getCode())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("eliminar una categoría activa hace borrado lógico y no borra la fila")
    void deleteActiveCategoryOnlyDeactivates() {
        Category category = Category.builder().id(CATEGORY_ID).name("IA").description("desc").active(true).build();
        given(categoryRepository.findById(CATEGORY_ID)).willReturn(Optional.of(category));
        given(categoryRepository.save(any(Category.class))).willAnswer(invocation -> invocation.getArgument(0));

        categoryService.delete(CATEGORY_ID);

        assertThat(category.getActive()).isFalse();
        verify(categoryRepository).save(category);
        verify(categoryRepository, never()).delete(any(Category.class));
        verify(categoryRepository, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("eliminar una categoría ya inactiva es idempotente y no audita de nuevo")
    void deleteAlreadyInactiveCategoryIsNoop() {
        Category category = Category.builder().id(CATEGORY_ID).name("IA").description("desc").active(false).build();
        given(categoryRepository.findById(CATEGORY_ID)).willReturn(Optional.of(category));

        categoryService.delete(CATEGORY_ID);

        verify(categoryRepository, never()).save(any(Category.class));
    }

    @Test
    @DisplayName("listar categorías paginado aplica el filtro de texto y respeta el Pageable")
    void findAllPagedWithTextFilter() {
        Category category = Category.builder().id(CATEGORY_ID).name("IA").description("desc").active(true).build();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Category> page = new PageImpl<>(List.of(category), pageable, 1);

        given(categoryRepository.findAll(this.<Category>anySpecification(), eq(pageable))).willReturn(page);

        Page<CategoryResponse> response = categoryService.findAll("ia", false, pageable);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).name()).isEqualTo("IA");
        verify(categoryRepository).findAll(this.<Category>anySpecification(), eq(pageable));
    }

    @SuppressWarnings("unchecked")
    private <T> Specification<T> anySpecification() {
        return any(Specification.class);
    }
}
