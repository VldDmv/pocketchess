package org.pocketchess.online.repo;

import org.pocketchess.online.domain.GameRecord;
import org.pocketchess.online.domain.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GameRecordRepository extends JpaRepository<GameRecord, Long> {

    Optional<GameRecord> findBySessionId(String sessionId);

    boolean existsBySessionId(String sessionId);

    @Query("select g from GameRecord g " +
           "where g.white = :user or g.black = :user " +
           "order by g.playedAt desc")
    List<GameRecord> findRecentByUser(@Param("user") User user, Pageable pageable);

    @Query("select count(g) from GameRecord g where g.white = :user or g.black = :user")
    long countByUser(@Param("user") User user);
}
