package ec.edu.ups.academicevents.users.mapper;

import ec.edu.ups.academicevents.users.dto.UserResponse;
import ec.edu.ups.academicevents.users.entity.User;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UserMapper {

    public UserResponse toResponse(User user, List<String> roles) {
        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getStatus(),
                roles,
                user.getCreatedAt());
    }
}
