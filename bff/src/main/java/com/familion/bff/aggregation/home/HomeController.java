package com.familion.bff.aggregation.home;

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
public class HomeController {

    private final AccessTokenResolver accessTokenResolver;
    private final HomeAggregationService homeAggregationService;

    public HomeController(
            AccessTokenResolver accessTokenResolver,
            HomeAggregationService homeAggregationService) {
        this.accessTokenResolver = accessTokenResolver;
        this.homeAggregationService = homeAggregationService;
    }

    @GetMapping("/home")
    public ResponseEntity<?> getHome(
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String lens,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String date,
            HttpServletRequest request) {
        Optional<String> accessToken = accessTokenResolver.resolve(request);
        if (accessToken.isEmpty()) {
            return unauthorizedResponse(request, "Missing access token.");
        }

        HomeQuery query = new HomeQuery(view, lens, granularity, date);
        HomeAggregationResult result = homeAggregationService.aggregate(accessToken.get(), query);
        if (result instanceof HomeAggregationResult.Ok ok) {
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
