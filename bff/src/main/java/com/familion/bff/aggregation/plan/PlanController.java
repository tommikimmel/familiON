package com.familion.bff.aggregation.plan;

import com.familion.bff.auth.AccessTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Optional;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bff")
public class PlanController {

    private final AccessTokenResolver accessTokenResolver;
    private final PlanAggregationService planAggregationService;

    public PlanController(
            AccessTokenResolver accessTokenResolver,
            PlanAggregationService planAggregationService) {
        this.accessTokenResolver = accessTokenResolver;
        this.planAggregationService = planAggregationService;
    }

    @GetMapping("/plan")
    public ResponseEntity<?> getPlan(
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String month,
            HttpServletRequest request) {
        Optional<String> accessToken = accessTokenResolver.resolve(request);
        if (accessToken.isEmpty()) {
            return unauthorizedResponse(request, "Missing access token.");
        }

        PlanQuery query = new PlanQuery(view, month);
        PlanAggregationResult result = planAggregationService.aggregate(accessToken.get(), query);
        if (result instanceof PlanAggregationResult.Ok ok) {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .body(ok.payload());
        }
        return unauthorizedResponse(request, "Access token rejected by core service.");
    }

    private ResponseEntity<ProblemDetail> unauthorizedResponse(HttpServletRequest request, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setTitle("Unauthorized");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
