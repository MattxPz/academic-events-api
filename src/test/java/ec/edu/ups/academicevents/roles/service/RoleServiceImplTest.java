package ec.edu.ups.academicevents.roles.service;

import ec.edu.ups.academicevents.roles.dto.RoleResponse;
import ec.edu.ups.academicevents.roles.entity.Role;
import ec.edu.ups.academicevents.roles.repository.RoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * NOTA: RoleService solo expone {@code findAll(Pageable)} (no hay un método
 * {@code findByName}, aunque {@link RoleRepository} sí lo tiene); por eso los
 * casos de "buscar por nombre" se adaptaron a lo que la capa de servicio
 * realmente soporta hoy.
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;

    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleServiceImpl(roleRepository);
    }

    @Test
    @DisplayName("listar todos los roles devuelve la página mapeada a RoleResponse")
    void findAllReturnsAllRolesMapped() {
        Pageable pageable = PageRequest.of(0, 10);
        Role role = Role.builder().id(1L).name("ADMIN").description("Administrador del sistema").build();
        given(roleRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(role), pageable, 1));

        Page<RoleResponse> response = roleService.findAll(pageable);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).name()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("listar roles cuando no hay ninguno devuelve una página vacía")
    void findAllWithNoRolesReturnsEmptyPage() {
        Pageable pageable = PageRequest.of(0, 10);
        given(roleRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<RoleResponse> response = roleService.findAll(pageable);

        assertThat(response.getContent()).isEmpty();
        assertThat(response.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("listar roles respeta el Pageable solicitado")
    void findAllRespectsRequestedPageable() {
        Pageable pageable = PageRequest.of(2, 5);
        given(roleRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(), pageable, 0));

        roleService.findAll(pageable);

        verify(roleRepository).findAll(pageable);
    }
}
