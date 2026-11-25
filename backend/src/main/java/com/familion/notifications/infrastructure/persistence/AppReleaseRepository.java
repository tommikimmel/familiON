package com.familion.notifications.infrastructure.persistence;

import com.familion.notifications.domain.AppRelease;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppReleaseRepository extends JpaRepository<AppRelease, String> {

    List<AppRelease> findAllByOrderByReleasedAtDesc();

    Optional<AppRelease> findFirstByOrderByReleasedAtDesc();
}
