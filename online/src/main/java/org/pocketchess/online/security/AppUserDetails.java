package org.pocketchess.online.security;

import org.pocketchess.online.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Adapts our {@link User} to Spring Security; principal name = display name. */
public class AppUserDetails implements UserDetails {

    private final Long userId;
    private final String displayName;
    private final String passwordHash;

    public AppUserDetails(User user) {
        this.userId = user.getId();
        this.displayName = user.getDisplayName();
        this.passwordHash = user.getPasswordHash() == null ? "" : user.getPasswordHash();
    }

    public Long getUserId() { return userId; }
    public String getDisplayName() { return displayName; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return displayName; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
