package com.dedicatedcode.reitti.repository;

import com.dedicatedcode.reitti.model.ImmichIntegration;
import com.dedicatedcode.reitti.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ImmichIntegrationRepository extends JpaRepository<ImmichIntegration, Long> {
    Optional<ImmichIntegration> findByUser(User user);
}
