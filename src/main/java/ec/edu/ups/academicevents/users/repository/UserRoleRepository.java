package ec.edu.ups.academicevents.users.repository;

import ec.edu.ups.academicevents.users.entity.UserRole;
import ec.edu.ups.academicevents.users.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {
}
