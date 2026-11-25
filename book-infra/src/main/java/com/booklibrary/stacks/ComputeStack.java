package com.booklibrary.stacks;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.iam.*;
import software.amazon.awscdk.services.lambda.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.*;
import software.constructs.Construct;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Lambda function for the API, with its execution role, artifact bucket and feature flags. */
public class ComputeStack extends Stack {

  private static final String APPCONFIG_PROFILE_NAME = "feature-flags-v3";

  // Initial flag values. Edit this file, or change flags later in AppConfig.
  private static final Path FEATURE_FLAGS_FILE = Path.of("feature-flags.json");
  private static final String APPCONFIG_EXTENSION_PORT = "2772";

  private final Function bookApiLambda;

  public ComputeStack(
      final Construct scope,
      final String id,
      final StackProps props,
      final NetworkStack networkStack,
      final DatabaseStack databaseStack) {
    super(scope, id, props);

    Bucket artifactBucket = createArtifactBucket();
    Role lambdaRole = createLambdaRole(databaseStack, artifactBucket);
    FeatureFlagsConfig featureFlags =
        new FeatureFlagsConfig(this, APPCONFIG_PROFILE_NAME, FEATURE_FLAGS_FILE);

    this.bookApiLambda = createApiFunction(networkStack, databaseStack, lambdaRole, featureFlags);

    CfnOutput.Builder.create(this, "LambdaFunctionArn")
        .description("ARN of the Book Library Lambda function")
        .value(bookApiLambda.getFunctionArn())
        .build();
  }

  /** Stores the Lambda JAR, versioned for easy roll back. */
  private Bucket createArtifactBucket() {
    return Bucket.Builder.create(this, "LambdaArtifactBucket")
        .bucketName("book-library-lambda-artifacts-" + this.getAccount())
        .versioned(true)
        .encryption(BucketEncryption.S3_MANAGED)
        .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
        .removalPolicy(RemovalPolicy.DESTROY)
        .autoDeleteObjects(true)
        .build();
  }

  /**
   * Least-privilege role: Lambda can only do what it needs.
   *
   * @param databaseStack {@link DatabaseStack}
   * @param artifactBucket {@link Bucket}
   * @return {@link Role} Lambda Role
   */
  private Role createLambdaRole(DatabaseStack databaseStack, Bucket artifactBucket) {
    Role role =
        Role.Builder.create(this, "LambdaExecutionRole")
            .roleName("book-library-lambda-role")
            .description("Execution role for Book Library Lambda function")
            .assumedBy(new ServicePrincipal("lambda.amazonaws.com"))
            .managedPolicies(
                List.of(
                    ManagedPolicy.fromAwsManagedPolicyName(
                        "service-role/AWSLambdaBasicExecutionRole"),
                    ManagedPolicy.fromAwsManagedPolicyName(
                        "service-role/AWSLambdaVPCAccessExecutionRole")))
            .build();

    role.addToPolicy(
        PolicyStatement.Builder.create()
            .actions(List.of("secretsmanager:GetSecretValue", "secretsmanager:DescribeSecret"))
            .resources(List.of(databaseStack.getDbSecret().getSecretArn()))
            .build());

    artifactBucket.grantRead(role);

    role.addToPolicy(
        PolicyStatement.Builder.create()
            .actions(
                List.of("appconfig:GetLatestConfiguration", "appconfig:StartConfigurationSession"))
            .resources(List.of("*"))
            .build());

    // Decrypt the Secrets Manager secret. Without this, Secrets Manager returns
    // "Access to KMS is not allowed".
    role.addToPolicy(
        PolicyStatement.Builder.create()
            .actions(List.of("kms:Decrypt", "kms:GenerateDataKey"))
            .resources(List.of(databaseStack.getKmsKey().getKeyArn()))
            .build());

    return role;
  }

  /**
   * Spring Boot JAR on Lambda, inside the VPC private subnets.
   *
   * @param networkStack {@link NetworkStack}
   * @param databaseStack {@link DatabaseStack}
   * @param role {@link Role}
   * @param featureFlags {@link FeatureFlagsConfig}
   * @return {@link Function}
   */
  private Function createApiFunction(
      NetworkStack networkStack,
      DatabaseStack databaseStack,
      Role role,
      FeatureFlagsConfig featureFlags) {
    // AppConfig Lambda extension layer (us-east-1 ARN)
    ILayerVersion appConfigExtension =
        LayerVersion.fromLayerVersionArn(
            this,
            "AppConfigExtensionLayer",
            "arn:aws:lambda:us-east-1:027255383542:layer:AWS-AppConfig-Extension:128");

    return Function.Builder.create(this, "BookApiLambda")
        .functionName("book-library-api")
        .description("Book Library REST API — Spring Boot on Lambda")
        .runtime(Runtime.JAVA_21)
        .architecture(Architecture.X86_64)
        .handler("com.booklibrary.handler.StreamLambdaHandler")
        .code(Code.fromAsset("../book-api/target/book-api-1.0.0.jar"))
        .memorySize(2048)
        .timeout(Duration.seconds(30))
        .vpc(networkStack.getVpc())
        .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_WITH_EGRESS).build())
        .securityGroups(List.of(networkStack.getLambdaSecurityGroup()))
        .role(role)
        .layers(List.of(appConfigExtension))
        .environment(lambdaEnvironment(databaseStack, featureFlags))
        .build();
  }

  /**
   * @param databaseStack {@link DatabaseStack}
   * @param featureFlags {@link FeatureFlagsConfig}
   * @return {@link Map}
   */
  private static Map<String, String> lambdaEnvironment(
      DatabaseStack databaseStack, FeatureFlagsConfig featureFlags) {
    return Map.ofEntries(
        Map.entry("SPRING_PROFILES_ACTIVE", "lambda"),
        Map.entry("DB_SECRET_ARN", databaseStack.getDbSecret().getSecretArn()),
        Map.entry("APPCONFIG_APP", featureFlags.getApplicationId()),
        Map.entry("APPCONFIG_ENV", featureFlags.getEnvironmentName()),
        Map.entry("APPCONFIG_PROFILE", featureFlags.getProfileName()),
        Map.entry("AWS_APPCONFIG_EXTENSION_HTTP_PORT", APPCONFIG_EXTENSION_PORT));
  }

  public Function getBookApiLambda() {
    return bookApiLambda;
  }
}
