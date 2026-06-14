package com.fu.math_copilot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties("ic.auth")
@Data
public class AuthProperties {
    private final List<String> includedPath;
    private final List<String> excludedPath;
}
