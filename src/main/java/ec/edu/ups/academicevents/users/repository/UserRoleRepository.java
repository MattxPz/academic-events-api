package ec.edu.ups.academicevents.users.repository;

import ec.edu.ups.academicevents.users.entity.UserRole;
import ec.edu.ups.academicevents.users.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    @Query("select r.name from UserRole ur join ur.role r where ur.user.id = :userId")
    List<String> findRoleNamesByUserId(@Param("userId") Long userId);

    void deleteByUser_Id(Long userId);
}
