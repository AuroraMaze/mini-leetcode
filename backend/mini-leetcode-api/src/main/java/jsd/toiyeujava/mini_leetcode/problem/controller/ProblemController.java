package jsd.toiyeujava.mini_leetcode.problem.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jsd.toiyeujava.mini_leetcode.problem.dto.CreateProblemRequest;
import jsd.toiyeujava.mini_leetcode.problem.dto.ProblemResponse;
import jsd.toiyeujava.mini_leetcode.problem.dto.UpdateProblemRequest;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.auth.repository.UserRepository;
import jsd.toiyeujava.mini_leetcode.auth.security.UserPrincipal;
import jsd.toiyeujava.mini_leetcode.problem.service.ProblemService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/problems")
@Tag(name = "Problems", description = "Problem Management and Exploration APIs")
public class ProblemController {

    private final ProblemService problemService;
    private final UserRepository userRepository;

    public ProblemController(ProblemService problemService, UserRepository userRepository) {
        this.problemService = problemService;
        this.userRepository = userRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Create a new problem", description = "Allows INSTRUCTOR and ADMIN to create a problem with auto-generated unique slug",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Problem successfully created"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden - requires INSTRUCTOR or ADMIN")
    })
    public ResponseEntity<ProblemResponse> createProblem(
            @Valid @RequestBody CreateProblemRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        User creator = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + principal.getId()));

        ProblemResponse response = problemService.createProblem(request, creator);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Search and list problems", description = "Returns a paginated list of problems with optional filtering by difficulty, status, or search query")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Successfully retrieved page of problems")
    })
    public ResponseEntity<Page<ProblemResponse>> getProblems(
            @Parameter(description = "Filter by difficulty (EASY, MEDIUM, HARD)")
            @RequestParam(required = false) Difficulty difficulty,
            @Parameter(description = "Filter by problem status (DRAFT, REVIEWING, PUBLISHED, ARCHIVED)")
            @RequestParam(required = false) ProblemStatus status,
            @Parameter(description = "Keyword search in title or description")
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        if (httpRequest != null) {
            String pageParam = httpRequest.getParameter("page");
            if (pageParam != null) {
                try {
                    int p = Integer.parseInt(pageParam);
                    if (p < 0) {
                        throw new IllegalArgumentException("Page index must not be less than zero");
                    }
                } catch (NumberFormatException ignored) {}
            }
            String sizeParam = httpRequest.getParameter("size");
            if (sizeParam != null) {
                try {
                    int s = Integer.parseInt(sizeParam);
                    if (s <= 0) {
                        throw new IllegalArgumentException("Page size must not be less than one");
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
        int clampedSize = Math.min(pageable.getPageSize(), 100);
        if (clampedSize != pageable.getPageSize()) {
            pageable = org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), clampedSize, pageable.getSort());
        }
        Page<ProblemResponse> page = problemService.getProblems(difficulty, status, search, pageable);
        return ResponseEntity.ok(page);
    }

    @GetMapping("/{idOrSlug}")
    @Operation(summary = "Get problem details by ID or Slug", description = "Retrieve a single problem by its numeric ID or slug string")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Problem found"),
            @ApiResponse(responseCode = "404", description = "Problem not found")
    })
    public ResponseEntity<ProblemResponse> getProblem(
            @Parameter(description = "Numeric ID or string slug of the problem")
            @PathVariable String idOrSlug) {
        ProblemResponse response;
        if (idOrSlug.matches("^\\d+$")) {
            try {
                response = problemService.getProblemById(Long.parseLong(idOrSlug));
            } catch (ResourceNotFoundException e) {
                response = problemService.getProblemBySlug(idOrSlug);
            }
        } else {
            response = problemService.getProblemBySlug(idOrSlug);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Get problem details by slug", description = "Retrieve a single problem explicitly by its slug")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Problem found"),
            @ApiResponse(responseCode = "404", description = "Problem not found")
    })
    public ResponseEntity<ProblemResponse> getProblemBySlug(@PathVariable String slug) {
        ProblemResponse response = problemService.getProblemBySlug(slug);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    @Operation(summary = "Update problem", description = "Updates an existing problem's details and lifecycle status",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Problem updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request payload"),
            @ApiResponse(responseCode = "404", description = "Problem not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ProblemResponse> updateProblem(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProblemRequest request) {
        ProblemResponse response = problemService.updateProblem(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Delete problem", description = "Deletes a problem (ADMIN only)",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Problem deleted successfully"),
            @ApiResponse(responseCode = "404", description = "Problem not found"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<Void> deleteProblem(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return ResponseEntity.noContent().build();
    }
}
