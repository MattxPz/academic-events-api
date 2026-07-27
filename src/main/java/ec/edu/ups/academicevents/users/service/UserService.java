package ec.edu.ups.academicevents.users.service;

import ec.edu.ups.academicevents.users.dto.UserResponse;
import ec.edu.ups.academicevents.users.dto.UserRolesRequest;
import ec.edu.ups.academicevents.users.dto.UserStatusRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    Page<UserResponse> findAll(String q, String status, Pageable pageable);

    UserResponse findById(Long id);

    UserResponse updateStatus(Long id, UserStatusRequest request);

    UserResponse replaceRoles(Long id, UserRolesRequest request);
}
