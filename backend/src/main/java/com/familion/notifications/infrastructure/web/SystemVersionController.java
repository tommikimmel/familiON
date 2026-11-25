package com.familion.notifications.infrastructure.web;

import com.familion.notifications.application.SystemVersionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemVersionController {

    private final SystemVersionService systemVersionService;

    public SystemVersionController(SystemVersionService systemVersionService) {
        this.systemVersionService = systemVersionService;
    }

    @GetMapping("/version")
    public SystemVersionResponse getVersion() {
        SystemVersionService.VersionInfo info = systemVersionService.getVersion();
        LatestReleaseResponse latest =
                info.latestRelease() != null
                        ? new LatestReleaseResponse(
                                info.latestRelease().version(),
                                info.latestRelease().title(),
                                info.latestRelease().releasedAt())
                        : null;
        return new SystemVersionResponse(info.version(), latest);
    }

    @GetMapping("/changelog")
    public List<ReleaseResponse> getChangelog() {
        return systemVersionService.getChangelog().stream()
                .map(
                        r ->
                                new ReleaseResponse(
                                        r.version(),
                                        r.title(),
                                        r.changelogMarkdown(),
                                        r.isBreaking(),
                                        r.releasedAt()))
                .toList();
    }
}
