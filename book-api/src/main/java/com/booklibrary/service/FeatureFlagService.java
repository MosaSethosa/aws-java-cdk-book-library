package com.booklibrary.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Reads AppConfig feature flags through the Lambda extension's local HTTP endpoint.
 *
 * <p>The extension returns {@code {"flagName": {"enabled": true}, ...}} for a feature-flag profile.
 * It polls AppConfig and caches the config, and results are cached here for a short time. If the
 * extension isn't configured or reachable (local runs, tests), each flag uses its default from
 * {@link FeatureFlag}.
 */
@Service
public class FeatureFlagService {

  private static final Logger log = LoggerFactory.getLogger(FeatureFlagService.class);

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final HttpClient httpClient =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build();
  private final String url;
  private final Duration cacheTtl;

  private JsonNode cachedFlags;
  private long cachedAtNanos;

  public FeatureFlagService(
      @Value("${featureflags.application:}") String application,
      @Value("${featureflags.environment:}") String environment,
      @Value("${featureflags.profile:}") String profile,
      @Value("${featureflags.port:2772}") int port,
      @Value("${featureflags.cache-ttl:15s}") Duration cacheTtl) {
    boolean configured = !application.isBlank() && !environment.isBlank() && !profile.isBlank();
    this.url =
        configured
            ? "http://localhost:%d/applications/%s/environments/%s/configurations/%s"
                .formatted(port, application, environment, profile)
            : null;
    this.cacheTtl = cacheTtl;
  }

  public boolean isEnabled(FeatureFlag flag) {
    JsonNode flags = loadFlags();
    if (flags == null) {
      return flag.defaultValue();
    }
    return flags.path(flag.key()).path("enabled").asBoolean(flag.defaultValue());
  }

  private synchronized JsonNode loadFlags() {
    if (url == null) {
      return null;
    }
    boolean fresh = cachedFlags != null && System.nanoTime() - cachedAtNanos < cacheTtl.toNanos();
    if (fresh) {
      return cachedFlags;
    }
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(2)).GET().build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() == 200) {
        cachedFlags = objectMapper.readTree(response.body());
        cachedAtNanos = System.nanoTime();
      } else {
        log.warn("AppConfig extension returned HTTP {}", response.statusCode());
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.warn("Could not read feature flags, using last known values or defaults", e);
    }
    return cachedFlags;
  }
}
