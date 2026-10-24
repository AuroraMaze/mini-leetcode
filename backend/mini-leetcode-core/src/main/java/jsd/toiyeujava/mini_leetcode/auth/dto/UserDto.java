package jsd.toiyeujava.mini_leetcode.auth.dto;

import jsd.toiyeujava.mini_leetcode.entity.Role;
import jsd.toiyeujava.mini_leetcode.entity.UserStatus;

public record UserDto(
        Long id,
        String username,
        String email,
        Role role,
        UserStatus status
) {
}
