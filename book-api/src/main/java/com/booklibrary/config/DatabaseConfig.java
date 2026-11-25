package com.booklibrary.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;

import javax.sql.DataSource;

@Configuration
@Profile("lambda") // Only active on Lambda, not locally
public class DatabaseConfig {

  @Value("${DB_SECRET_ARN}")
  private String dbSecretArn;

  @Bean
  public DataSource dataSource() {
    String secretJson = fetchSecret(dbSecretArn);
    try {
      JsonNode secret = new ObjectMapper().readTree(secretJson);
      String url =
          String.format(
              "jdbc:postgresql://%s:%s/%s",
              secret.get("host").asText(),
              secret.get("port").asText(),
              secret.get("dbname").asText());
      return DataSourceBuilder.create()
          .driverClassName("org.postgresql.Driver")
          .url(url)
          .username(secret.get("username").asText())
          .password(secret.get("password").asText())
          .build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse DB secret", e);
    }
  }

  private String fetchSecret(String secretArn) {
    try (SecretsManagerClient client =
        SecretsManagerClient.builder().region(Region.of(System.getenv("AWS_REGION"))).build()) {
      return client
          .getSecretValue(GetSecretValueRequest.builder().secretId(secretArn).build())
          .secretString();
    }
  }
}
