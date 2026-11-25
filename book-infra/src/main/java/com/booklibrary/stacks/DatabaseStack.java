package com.booklibrary.stacks;

import lombok.Getter;
import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.kms.Key;
import software.amazon.awscdk.services.kms.KeySpec;
import software.amazon.awscdk.services.kms.KeyUsage;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.secretsmanager.Secret;
import software.amazon.awscdk.services.secretsmanager.SecretStringGenerator;
import software.constructs.Construct;

import java.util.List;

/** KMS key, generated database credentials in Secrets Manager, and the RDS PostgreSQL instance. */
@Getter
public class DatabaseStack extends Stack {

  private static final String DB_USERNAME = "dbadmin";

  private final Secret dbSecret;
  private final DatabaseInstance dbInstance;
  private final Key kmsKey;

  public DatabaseStack(Construct scope, String id, StackProps props, NetworkStack networkStack) {
    super(scope, id, props);

    this.kmsKey = createKmsKey();
    this.dbSecret = createDbSecret();
    this.dbInstance = createDbInstance(networkStack);
  }

  /**
   * Encrypts both the credentials secret and the database storage.
   *
   * @return {@link Key} KMS key
   */
  private Key createKmsKey() {
    return Key.Builder.create(this, "BookLibraryKmsKey")
        .description("KMS Ket for encrypting Book Library Secrets")
        .keySpec(KeySpec.SYMMETRIC_DEFAULT)
        .keyUsage(KeyUsage.ENCRYPT_DECRYPT)
        .enableKeyRotation(true)
        .removalPolicy(RemovalPolicy.DESTROY)
        .build();
  }

  /**
   * Generated credentials: fixed username, random 32-character password without punctuation.
   *
   * @return {@link Secret} DB Secret
   */
  private Secret createDbSecret() {
    return Secret.Builder.create(this, "BookLibraryDbSecret")
        .secretName("book-library/db-credentials")
        .description("RDS PostgreSQL credentials for Book Library")
        .encryptionKey(kmsKey)
        .generateSecretString(
            SecretStringGenerator.builder()
                .secretStringTemplate("{\"username\": \"" + DB_USERNAME + "\"}")
                .generateStringKey("password")
                .passwordLength(32)
                .excludePunctuation(true)
                .build())
        .build();
  }

  /**
   * PostgreSQL in the private subnets, not publicly accessible, reachable only from Lambda.
   *
   * @param networkStack {@link NetworkStack}
   * @return {@link DatabaseInstance}
   */
  private DatabaseInstance createDbInstance(NetworkStack networkStack) {
    return DatabaseInstance.Builder.create(this, "BookLibraryRDS")
        .instanceIdentifier("book-library-db")
        .engine(
            DatabaseInstanceEngine.postgres(
                PostgresInstanceEngineProps.builder()
                    .version(PostgresEngineVersion.VER_16_3)
                    .build()))
        .instanceType(InstanceType.of(InstanceClass.T4G, InstanceSize.MICRO))
        .credentials(Credentials.fromSecret(dbSecret))
        .vpc(networkStack.getVpc())
        .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PRIVATE_WITH_EGRESS).build())
        .securityGroups(List.of(networkStack.getRdsSecurityGroup()))
        .publiclyAccessible(false)
        .databaseName("booklibrarydb")
        .storageEncrypted(true)
        .storageEncryptionKey(kmsKey)
        .backupRetention(Duration.days(7))
        .preferredMaintenanceWindow("Sun:02:00-Sun:03:00")
        .removalPolicy(RemovalPolicy.DESTROY)
        .deletionProtection(false)
        .build();
  }
}
