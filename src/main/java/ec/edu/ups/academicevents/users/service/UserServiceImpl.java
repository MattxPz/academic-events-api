package ec.edu.ups.academicevents.users.service;

import ec.edu.ups.academicevents.roles.entity.Role;
import ec.edu.ups.academicevents.roles.repository.RoleRepository;
import ec.edu.ups.academicevents.shared.audit.AuditPayloads;
import ec.edu.ups.academicevents.shared.audit.AuditService;
import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.exception.ResourceNotFoundException;
import ec.edu.ups.academicevents.users.dto.UserResponse;
import ec.edu.ups.academicevents.users.dto.UserRolesRequest;
import ec.edu.ups.academicevents.users.dto.UserStatusRequest;
import ec.edu.ups.academicevents.users.entity.User;
import ec.edu.ups.academicevents.users.entity.UserRole;
import ec.edu.ups.academicevents.users.entity.UserRoleId;
import ec.edu.ups.academicevents.users.mapper.UserMapper;
import ec.edu.ups.academicevents.users.repository.UserRepository;
import ec.edu.ups.academicevents.users.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final String AUDIT_RESOURCE_USER = "USER";
    private static final String AUDIT_USER_STATUS_CHANGED = "USER_STATUS_CHANGED";
    private static final String AUDIT_USER_ROLES_UPDATED = "USER_ROLES_UPDATED";

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final AuditService auditService;

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(String q, String status, Pageable pageable) {
        Specification<User> specification = (root, query, cb) -> cb.conjunction();

        if (q != null && !q.isBlank()) {
            String likePattern = "%" + q.trim().toLowerCase() + "%";
            specification = specification.and((root, query, cb) -> cb.or(
                    cb.like(cb.lower(root.get("firstName")), likePattern),
                    cb.like(cb.lower(root.get("lastName")), likePattern),
                    cb.like(cb.lower(root.get("email")), likePattern)));
        }

        if (status != null && !status.isBlank()) {
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        return userRepository.findAll(specification, pageable)
                .map(user -> userMapper.toResponse(user, userRoleRepository.findRoleNamesByUserId(user.getId())));
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        User user = findUserOrThrow(id);
        return userMapper.toResponse(user, userRoleRepository.findRoleNamesByUserId(id));
    }

    @Override
    @Transactional
    public UserResponse updateStatus(Long id, UserStatusRequest request) {
        User user = findUserOrThrow(id);
        String previousStatus = user.getStatus();

        user.setStatus(request.status());
        user = userRepository.save(user);

        auditService.recordSuccess(AUDIT_USER_STATUS_CHANGED, AUDIT_RESOURCE_USER, id,
                AuditPayloads.status(previousStatus), AuditPayloads.status(user.getStatus()));

        return userMapper.toResponse(user, userRoleRepository.findRoleNamesByUserId(id));
    }

    @Override
    @Transactional
    public UserResponse replaceRoles(Long id, UserRolesRequest request) {
        User user = findUserOrThrow(id);
        List<String> previousRoles = userRoleRepository.findRoleNamesByUserId(id);

        Set<Long> requestedRoleIds = new HashSet<>(request.roleIds());
        List<Role> roles = roleRepository.findAllById(requestedRoleIds);
        if (roles.size() != requestedRoleIds.size()) {
            throw new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, "Uno o más roles no existen.");
        }

        userRoleRepository.deleteByUser_Id(id);
        userRoleRepository.flush();

        List<UserRole> newUserRoles = roles.stream()
                .map(role -> UserRole.builder()
                        .id(new UserRoleId(id, role.getId()))
                        .user(user)
                        .role(role)
                        .build())
                .toList();
        userRoleRepository.saveAll(newUserRoles);

        List<String> updatedRoles = userRoleRepository.findRoleNamesByUserId(id);

        auditService.recordSuccess(AUDIT_USER_ROLES_UPDATED, AUDIT_RESOURCE_USER, id,
                AuditPayloads.roles(previousRoles), AuditPayloads.roles(updatedRoles));

        return userMapper.toResponse(user, updatedRoles);
    }

    private User findUserOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.RESOURCE_NOT_FOUND, "No se encontró el usuario solicitado."));
    }
}
