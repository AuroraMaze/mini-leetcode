package jsd.toiyeujava.mini_leetcode.problem.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;

public record CreateProblemRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must not exceed 200 characters")
        String title,

        @NotBlank(message = "Description is required")
        String description,

        @NotNull(message = "Difficulty is required")
        Difficulty difficulty,

        @Min(value = 100, message = "Time limit must be at least 100ms")
        Integer timeLimitMs,

        @Min(value = 16, message = "Memory limit must be at least 16MB")
        Integer memoryLimitMb
) {
    public Integer getTimeLimitMsOrDefault() {
        return timeLimitMs != null ? timeLimitMs : 1000;
    }

    public Integer getMemoryLimitMbOrDefault() {
        return memoryLimitMb != null ? memoryLimitMb : 256;
    }
}
