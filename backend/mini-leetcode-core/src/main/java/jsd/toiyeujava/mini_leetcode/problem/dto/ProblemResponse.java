package jsd.toiyeujava.mini_leetcode.problem.dto;

import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;

import java.time.LocalDateTime;

public record ProblemResponse(
        Long id,
        String title,
        String slug,
        String description,
        Difficulty difficulty,
        Integer timeLimitMs,
        Integer memoryLimitMb,
        ProblemStatus status,
        Long createdById,
        String createdByUsername,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ProblemResponse fromEntity(Problem p) {
        return new ProblemResponse(
                p.getId(),
                p.getTitle(),
                p.getSlug(),
                p.getDescription(),
                p.getDifficulty(),
                p.getTimeLimitMs(),
                p.getMemoryLimitMb(),
                p.getStatus(),
                p.getCreatedBy() != null ? p.getCreatedBy().getId() : null,
                p.getCreatedBy() != null ? p.getCreatedBy().getUsername() : null,
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
