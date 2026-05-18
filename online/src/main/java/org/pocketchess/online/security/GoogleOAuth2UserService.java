package org.pocketchess.online.security;

import org.pocketchess.online.domain.User;
import org.pocketchess.online.service.UserService;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Upserts the matching {@link User} on first Google sign-in and exposes the
 * stored display name as the OAuth2 principal name so downstream code can
 * treat form-logins and Google-logins uniformly.
 */
@Service
public class GoogleOAuth2UserService extends DefaultOAuth2UserService {

    private final UserService userService;

    public GoogleOAuth2UserService(UserService userService) {
        this.userService = userService;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User googleUser = super.loadUser(userRequest);
        String sub = googleUser.getAttribute("sub");
        String name = googleUser.getAttribute("name");

        User user = userService.upsertGoogleUser(sub, name);

        Map<String, Object> attrs = new HashMap<>(googleUser.getAttributes());
        attrs.put("displayName", user.getDisplayName());
        attrs.put("userId", user.getId());

        return new DefaultOAuth2User(
                List.of(() -> "ROLE_USER"),
                attrs,
                "displayName");
    }
}
