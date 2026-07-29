package ec.edu.ups.academicevents.users.service;

import ec.edu.ups.academicevents.roles.entity.Role;
import ec.edu.ups.academicevents.roles.repository.RoleRepository;
import ec.edu.ups.academicevents.shared.audit.AuditService;
import ec.edu.ups.academicevents.shared.exception.ResourceNotFoundException;
import ec.edu.ups.academicevents.users.dto.UserResponse;
import ec.edu.ups.academicevents.users.dto.UserRolesRequest;
import ec.edu.ups.academicevents.users.dto.UserStatusRequest;
import ec.edu.ups.academicevents.users.entity.User;
import ec.edu.ups.academicevents.users.mapper.UserMapper;
import ec.edu.ups.academicevents.users.repository.UserRepository;
import ec.edu.ups.academicevents.users.repository.UserRoleRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    private static final Long USER_ID = 1L;
    private static final Long ROLE_ID = 2L;

    @Mock
    private UserRepository userRepository;
    @Mock
    private UserRoleRepository userRoleRepository;
    @Mock
    private RoleRepository roleRepository;
    @Spy
    private UserMapper userMapper = new UserMapper();
    @Mock
    private AuditService auditService;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userRepository, userRoleRepository, roleRepository, userMapper, auditService);
    }

    @Test
    @DisplayName("listar usuarios paginado aplica el filtro de texto y respeta el Pageable")
    void findAllPagedWithTextFilter() {
        User user = activeUser();
        Pageable pageable = PageRequest.of(0, 10);
        Page<User> page = new PageImpl<>(List.of(user), pageable, 1);

        given(userRepository.findAll(this.<User>anySpecification(), eq(pageable))).willReturn(page);
        given(userRoleRepository.findRoleNamesByUserId(USER_ID)).willReturn(List.of("PARTICIPANT"));

        Page<UserResponse> response = userService.findAll("paula", null, pageable);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).roles()).containsExactly("PARTICIPANT");
        verify(userRepository).findAll(this.<User>anySpecification(), eq(pageable));
    }

    @Test
    @DisplayName("buscar un usuario por id inexistente lanza ResourceNotFoundException")
    void findByIdNonExistentFails() {
        given(userRepository.findById(USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(USER_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("cambiar el status de un usuario a BLOCKED lo persiste y lo audita")
    void updateStatusToBlockedPersistsAndAudits() {
        User user = activeUser();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
        given(userRoleRepository.findRoleNamesByUserId(USER_ID)).willReturn(List.of("PARTICIPANT"));

        UserResponse response = userService.updateStatus(USER_ID, new UserStatusRequest("BLOCKED"));

        assertThat(response.status()).isEqualTo("BLOCKED");
        assertThat(user.getStatus()).isEqualTo("BLOCKED");
        verify(userRepository).save(user);
        verify(auditService).recordSuccess(
                eq("USER_STATUS_CHANGED"), eq("USER"), eq(USER_ID), any(), any());
    }

    @Test
    @DisplayName("reemplazar los roles de un usuario borra los antiguos y crea los nuevos")
    void replaceRolesDeletesOldAndCreatesNew() {
        User user = activeUser();
        Role role = Role.builder().id(ROLE_ID).name("ORGANIZER").description("Organizador").build();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userRoleRepository.findRoleNamesByUserId(USER_ID))
                .willReturn(List.of("PARTICIPANT"))
                .willReturn(List.of("ORGANIZER"));
        given(roleRepository.findAllById(Set.of(ROLE_ID))).willReturn(List.of(role));

        UserResponse response = userService.replaceRoles(USER_ID, new UserRolesRequest(List.of(ROLE_ID)));

        assertThat(response.roles()).containsExactly("ORGANIZER");
        verify(userRoleRepository).deleteByUser_Id(USER_ID);
        verify(userRoleRepository).saveAll(any());
        verify(auditService).recordSuccess(
                eq("USER_ROLES_UPDATED"), eq("USER"), eq(USER_ID), any(), any());
    }

    @Test
    @DisplayName("reemplazar roles con un id inexistente lanza ResourceNotFoundException")
    void replaceRolesWithNonExistentRoleFails() {
        User user = activeUser();
        given(userRepository.findById(USER_ID)).willReturn(Optional.of(user));
        given(userRoleRepository.findRoleNamesByUserId(USER_ID)).willReturn(List.of());
        given(roleRepository.findAllById(Set.of(ROLE_ID))).willReturn(List.of());

        assertThatThrownBy(() -> userService.replaceRoles(USER_ID, new UserRolesRequest(List.of(ROLE_ID))))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(userRoleRepository, org.mockito.Mockito.never()).deleteByUser_Id(any());
    }

    @Test
    @DisplayName("una solicitud de reemplazo de roles con lista vacía falla la validación del DTO")
    void userRolesRequestWithEmptyListFailsValidation() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();

            Set<ConstraintViolation<UserRolesRequest>> violations =
                    validator.validate(new UserRolesRequest(List.of()));

            assertThat(violations).isNotEmpty();
        }
    }

    private User activeUser() {
        return User.builder()
                .id(USER_ID)
                .firstName("Paula")
                .lastName("Castillo")
                .email("paula.castillo@academic.test")
                .status("ACTIVE")
                .build();
    }

    @SuppressWarnings("unchecked")
    private <T> Specification<T> anySpecification() {
        return any(Specification.class);
    }
}
