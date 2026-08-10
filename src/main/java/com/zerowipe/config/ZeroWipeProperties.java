package com.zerowipe.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code zerowipe.*} settings from application.yml.
 */
@ConfigurationProperties(prefix = "zerowipe")
public record ZeroWipeProperties(
        boolean allowLiveMode,
        String operatorName,
        String organization,
        List<String> testDiskAllowlist) {
}
