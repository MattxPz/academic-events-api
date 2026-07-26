package ec.edu.ups.academicevents.shared.security;

import ec.edu.ups.academicevents.users.entity.User;
import ec.edu.ups.academicevents.users.repository.UserRepository;
import ec.edu.ups.academicevents.users.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    public UserDetails loadUserByUsername(String email) {
        User user = userRepository.findByEmail(email.toLowerCase())
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado."));

        return new CustomUserDetails(user, userRoleRepository.findRoleNamesByUserId(user.getId()));
    }
}
