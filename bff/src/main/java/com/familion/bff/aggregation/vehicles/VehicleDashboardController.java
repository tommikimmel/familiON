package com.familion.bff.aggregation.vehicles;

import com.familion.bff.auth.AccessTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bff")
public class VehicleDashboardController {

    private static final Pattern CANONICAL_UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final AccessTokenResolver accessTokenResolver;
    private final VehicleDashboardAggregationService vehicleDashboardAggregationService;

    public VehicleDashboardController(
            AccessTokenResolver accessTokenResolver,
            VehicleDashboardAggregationService vehicleDashboardAggregationService) {
        this.accessTokenResolver = accessTokenResolver;
        this.vehicleDashboardAggregationService = vehicleDashboardAggregationService;
    }

    @GetMapping("/vehicles/{id}/dashboard")
    public ResponseEntity<?> getVehicleDashboard(
            @PathVariable String id,
            @RequestParam(required = false) String lens,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpServletRequest request) {
        Optional<String> accessToken = accessTokenResolver.resolve(request);
        if (accessToken.isEmpty()) {
            return problemResponse(request, HttpStatus.UNAUTHORIZED, "Unauthorized", "Missing access token.");
        }

        Optional<UUID> vehicleId = parseUuid(id);
        if (vehicleId.isEmpty()) {
            return problemResponse(
                    request,
                    HttpStatus.BAD_REQUEST,
                    "Bad Request",
                    "Vehicle id must be a valid UUID.");
        }

        VehicleDashboardQuery query = new VehicleDashboardQuery(lens, from, to);
        VehicleDashboardResult result = vehicleDashboardAggregationService.aggregate(
                accessToken.get(),
                vehicleId.get(),
                query);

        return switch (result) {
            case VehicleDashboardResult.Ok ok -> ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(ok.payload());
            case VehicleDashboardResult.NotFound ignored -> problemResponse(
                    request,
                    HttpStatus.NOT_FOUND,
                    "Not Found",
                    "Vehicle not found.");
            case VehicleDashboardResult.Unauthorized ignored -> problemResponse(
                    request,
                    HttpStatus.UNAUTHORIZED,
                    "Unauthorized",
                    "Access token rejected by core service.");
        };
    }

    private Optional<UUID> parseUuid(String raw) {
        if (raw == null || !CANONICAL_UUID_PATTERN.matcher(raw).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private ResponseEntity<ProblemDetail> problemResponse(
            HttpServletRequest request,
            HttpStatus status,
            String title,
            String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
