package com.booklibrary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates the OpenAPI spec for the API from the code itself.
 *
 * <p>Runs during {@code mvn package}. It starts the app against an in-memory H2 database, asks
 * springdoc for the spec it builds from {@code BookController} and {@code Book} (routes,
 * parameters, and the {@code @NotBlank} / {@code @NotNull} rules), and writes it to {@code
 * target/openapi.json}.
 *
 * <p>book-infra's {@code ApiStack} reads that file to define API Gateway, so the controller is the
 * single source of truth: add an endpoint here, rebuild, redeploy.
 */
@SpringBootTest(
    properties = {
      // Document responses as JSON instead of springdoc's default "*/*"
      "springdoc.default-produces-media-type=application/json",
      "springdoc.writer-with-default-pretty-printer=true"
    })
@AutoConfigureMockMvc
class OpenApiSpecGeneratorTest {

  private static final Path OUTPUT = Path.of("target", "openapi.json");

  @Autowired private MockMvc mockMvc;

  @Test
  void writesOpenApiSpecFromController() throws Exception {
    String spec =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    Files.createDirectories(OUTPUT.getParent());
    Files.writeString(OUTPUT, spec);

    // Fail the build if the controller's routes are missing, rather than
    // letting CDK deploy an empty API.
    assertThat(spec).contains("\"/books\"").contains("\"/books/{id}\"");
  }
}
