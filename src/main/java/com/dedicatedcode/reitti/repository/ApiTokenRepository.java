package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ApiToken;
import com.dedicatedcode.reitti.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {
    Optional<ApiToken> findByToken(String token);

    List<ApiToken> findByUser(User user);
}
