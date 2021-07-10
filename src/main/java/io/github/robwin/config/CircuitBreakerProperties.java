package io.github.robwin.config;

import io.github.resilience4j.circuitbreaker.configure.CircuitBreakerConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "resilience4j.circuitbreaker")
@Component
public class CircuitBreakerProperties extends CircuitBreakerConfigurationProperties {

}