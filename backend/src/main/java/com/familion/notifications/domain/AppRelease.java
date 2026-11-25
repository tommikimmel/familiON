package com.familion.notifications.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "app_releases")
public class AppRelease {

    @Id
    @Column(name = "version", nullable = false, length = 30)
    private String version;

    @Column(name = "title", nullable = false, length = 140)
    private String title;

    @Column(name = "changelog_markdown", nullable = false, columnDefinition = "TEXT")
    private String changelogMarkdown;

    @Column(name = "is_breaking", nullable = false)
    private boolean isBreaking;

    @Column(name = "released_at", nullable = false)
    private Instant releasedAt;

    protected AppRelease() {}

    public AppRelease(
            String version,
            String title,
            String changelogMarkdown,
            boolean isBreaking,
            Instant releasedAt) {
        this.version = version;
        this.title = title;
        this.changelogMarkdown = changelogMarkdown;
        this.isBreaking = isBreaking;
        this.releasedAt = releasedAt != null ? releasedAt : Instant.now();
    }

    public String getVersion() {
        return version;
    }

    public String getTitle() {
        return title;
    }

    public String getChangelogMarkdown() {
        return changelogMarkdown;
    }

    public boolean isBreaking() {
        return isBreaking;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }
}
