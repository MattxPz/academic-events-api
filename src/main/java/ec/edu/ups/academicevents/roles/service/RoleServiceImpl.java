package ec.edu.ups.academicevents.roles.service;

import ec.edu.ups.academicevents.roles.dto.RoleResponse;
import ec.edu.ups.academicevents.roles.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;

    @Override
    @Transactional(readOnly = true)
    public Page<RoleResponse> findAll(Pageable pageable) {
        return roleRepository.findAll(pageable)
                .map(role -> new RoleResponse(role.getId(), role.getName(), role.getDescription()));
    }
}
