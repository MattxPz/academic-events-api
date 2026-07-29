package ec.edu.ups.academicevents.roles.service;

import ec.edu.ups.academicevents.roles.dto.RoleResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface RoleService {

    Page<RoleResponse> findAll(Pageable pageable);
}
