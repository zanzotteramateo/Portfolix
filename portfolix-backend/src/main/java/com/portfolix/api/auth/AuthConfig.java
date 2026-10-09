package com.portfolix.api.auth;

import com.portfolix.api.auth.token.EmailTokenProperties;
import com.portfolix.api.auth.token.RefreshTokenProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RefreshTokenProperties.class, EmailTokenProperties.class, LoginLockoutProperties.class})
public class AuthConfig {
}
