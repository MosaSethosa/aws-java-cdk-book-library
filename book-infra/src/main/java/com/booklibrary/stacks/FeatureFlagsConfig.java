package com.booklibrary.stacks;

import software.amazon.awscdk.services.appconfig.*;
import software.constructs.Construct;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AppConfig resources for the feature flags: application, environment, feature-flag profile, the
 * initial flag values, and a deployment of those values.
 *
 * <p>This is deliberately not a {@code Construct}. A construct would nest these resources under a
 * new ID, which changes their CloudFormation logical IDs and makes CloudFormation replace them. The
 * resources are created directly in the scope passed in, so their IDs stay as they were.
 */
final class FeatureFlagsConfig {

  private static final String ENVIRONMENT_NAME = "prod";

  private final CfnApplication application;
  private final String profileName;

  /**
   * @param scope the stack to create the resources in
   * @param profileName AppConfig profile name. The name can't be changed in place together with the
   *     type, so changing it means giving the profile a new construct ID as well.
   * @param initialFlagsFile JSON in AppConfig feature-flag format with the initial flag values
   */
  FeatureFlagsConfig(Construct scope, String profileName, Path initialFlagsFile) {
    this.profileName = profileName;

    this.application =
        CfnApplication.Builder.create(scope, "AppConfigApplication").name("book-library").build();

    CfnEnvironment environment =
        CfnEnvironment.Builder.create(scope, "AppConfigEnvironment")
            .applicationId(application.getRef())
            .name(ENVIRONMENT_NAME)
            .build();

    CfnConfigurationProfile profile =
        CfnConfigurationProfile.Builder.create(scope, "FeatureFlagsProfile")
            .applicationId(application.getRef())
            .name(profileName)
            .locationUri("hosted")
            .type("AWS.AppConfig.FeatureFlags")
            .build();

    CfnHostedConfigurationVersion version =
        CfnHostedConfigurationVersion.Builder.create(scope, "AppConfigVersion")
            .applicationId(application.getRef())
            .configurationProfileId(profile.getRef())
            .contentType("application/json")
            .content(read(initialFlagsFile))
            .build();

    CfnDeploymentStrategy allAtOnce =
        CfnDeploymentStrategy.Builder.create(scope, "AppConfigDeploymentStrategy")
            .name("AllAtOnce")
            .deploymentDurationInMinutes(0)
            .growthFactor(100)
            .finalBakeTimeInMinutes(0)
            .replicateTo("NONE")
            .build();

    CfnDeployment.Builder.create(scope, "AppConfigDeployment")
        .applicationId(application.getRef())
        .environmentId(environment.getRef())
        .configurationProfileId(profile.getRef())
        .configurationVersion(version.getRef())
        .deploymentStrategyId(allAtOnce.getRef())
        .build();
  }

  String getApplicationId() {
    return application.getRef();
  }

  String getEnvironmentName() {
    return ENVIRONMENT_NAME;
  }

  String getProfileName() {
    return profileName;
  }

  private static String read(Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + file.toAbsolutePath(), e);
    }
  }
}
