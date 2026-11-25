package com.familion.notifications.application;

import com.familion.notifications.domain.AppRelease;
import com.familion.notifications.infrastructure.persistence.AppReleaseRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SystemVersionService {

    public record LatestReleaseInfo(String version, String title, Instant releasedAt) {}

    public record VersionInfo(String version, LatestReleaseInfo latestRelease) {}

    public record ReleaseInfo(
            String version,
            String title,
            String changelogMarkdown,
            boolean isBreaking,
            Instant releasedAt) {}

    private final String currentVersion;
    private final AppReleaseRepository releaseRepository;

    public SystemVersionService(
            @Value("${familion.app.version:0.1.0}") String currentVersion,
            AppReleaseRepository releaseRepository) {
        this.currentVersion = currentVersion;
        this.releaseRepository = releaseRepository;
    }

    public VersionInfo getVersion() {
        LatestReleaseInfo latestRelease =
                releaseRepository
                        .findFirstByOrderByReleasedAtDesc()
                        .map(r -> new LatestReleaseInfo(r.getVersion(), r.getTitle(), r.getReleasedAt()))
                        .orElse(null);
        return new VersionInfo(currentVersion, latestRelease);
    }

    public List<ReleaseInfo> getChangelog() {
        return releaseRepository.findAllByOrderByReleasedAtDesc().stream()
                .map(
                        r ->
                                new ReleaseInfo(
                                        r.getVersion(),
                                        r.getTitle(),
                                        r.getChangelogMarkdown(),
                                        r.isBreaking(),
                                        r.getReleasedAt()))
                .toList();
    }
}
