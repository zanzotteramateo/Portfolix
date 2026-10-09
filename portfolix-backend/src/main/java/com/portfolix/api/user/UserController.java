package com.portfolix.api.user;

import com.portfolix.api.security.CurrentUserId;
import com.portfolix.api.user.dto.UserResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public UserResponse me(@CurrentUserId Long userId) {
        return userService.getProfile(userId);
    }
}
