package org.pocketchess.online.security;

import org.pocketchess.online.domain.User;
import org.pocketchess.online.repo.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String displayName) {
        User user = users.findByDisplayName(displayName)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + displayName));
        if (user.getPasswordHash() == null) {
            // OAuth-only account — no form login allowed.
            throw new UsernameNotFoundException("Account has no password; sign in with Google.");
        }
        return new AppUserDetails(user);
    }
}
