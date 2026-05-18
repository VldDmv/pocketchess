package org.pocketchess.online.repo;

import org.pocketchess.online.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByDisplayName(String displayName);

    Optional<User> findByGoogleSub(String googleSub);

    boolean existsByDisplayName(String displayName);
}
