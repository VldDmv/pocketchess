package org.pocketchess.online.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.security.Principal;

/** Utility for pulling the user's display name from any of our auth principals. */
public final class CurrentUser {

    private CurrentUser() {}

    public static String displayNameOf(Principal principal) {
        if (principal == null) return null;
        if (principal instanceof Authentication auth) {
            Object p = auth.getPrincipal();
            if (p instanceof AppUserDetails u) return u.getDisplayName();
            if (p instanceof OAuth2User o) {
                Object name = o.getAttributes().get("displayName");
                if (name != null) return name.toString();
                return o.getName();
            }
            return auth.getName();
        }
        return principal.getName();
    }
}
