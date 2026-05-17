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

    /** Accepts either the display name or the email to give players a forgiving login. */
    @Override
    public UserDetails loadUserByUsername(String identifier) {
        User user = users.findByDisplayName(identifier)
                .or(() -> users.findByEmail(identifier.toLowerCase()))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + identifier));
        if (user.getPasswordHash() == null) {
            // OAuth-only account — no form login allowed.
            throw new UsernameNotFoundException("Account has no password; sign in with Google.");
        }
        return new AppUserDetails(user);
    }
}
