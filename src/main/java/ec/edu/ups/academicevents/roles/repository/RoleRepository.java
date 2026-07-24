package ec.edu.ups.academicevents.roles.repository;

import ec.edu.ups.academicevents.roles.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}
