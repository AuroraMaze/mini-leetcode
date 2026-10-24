package jsd.toiyeujava.mini_leetcode.problem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;

public record UpdateProblemRequest(
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        String description,
        Difficulty difficulty,

        @Min(value = 100, message = "Time limit must be at least 100ms")
        Integer timeLimitMs,

        @Min(value = 16, message = "Memory limit must be at least 16MB")
        Integer memoryLimitMb,

        ProblemStatus status
) {
}
