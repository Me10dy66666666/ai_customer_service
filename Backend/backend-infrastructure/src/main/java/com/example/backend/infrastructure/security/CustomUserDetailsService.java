package com.example.backend.infrastructure.security;

import com.example.backend.domain.auth.model.User;
import com.example.backend.domain.auth.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.ArrayList;
import java.util.List;

public class CustomUserDetailsService implements UserDetailsService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        if (user.getRoles() != null) {
            for (com.example.backend.domain.auth.model.Role role : user.getRoles()) {
                if (role.getRoleName() != null) {
                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role.getRoleName()));
                }
            }
        }

        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                !user.isDisabled(),
                true,
                true,
                true,
                authorities
        );
    }
}
