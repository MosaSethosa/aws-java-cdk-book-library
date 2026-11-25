package com.booklibrary.stacks;

import lombok.Getter;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;

import java.util.List;
import java.util.Locale;

/** VPC with public and private subnets, plus the security groups for Lambda and RDS. */
@Getter
public class NetworkStack extends Stack {

  private static final int POSTGRES_PORT = 5432;
  private static final int HTTPS_PORT = 443;

  private final Vpc vpc;
  private final SecurityGroup lambdaSecurityGroup;
  private final SecurityGroup rdsSecurityGroup;

  public NetworkStack(Construct scope, String id, StackProps props) {
    super(scope, id, props);

    this.vpc = createVpc();
    this.lambdaSecurityGroup = createSecurityGroup("LambdaSecurityGroup", "Lambda");
    this.rdsSecurityGroup = createSecurityGroup("RdsSecurityGroup", "RDS");

    allowLambdaToReachRds();
    allowLambdaToReachAwsServices();
  }

  private Vpc createVpc() {
    return Vpc.Builder.create(this, "BookLibraryVPC")
        .vpcName("book-library-vpc")
        .ipAddresses(IpAddresses.cidr("10.0.0.0/16"))
        .maxAzs(2)
        .subnetConfiguration(
            List.of(
                subnet("Public", SubnetType.PUBLIC),
                subnet("Private", SubnetType.PRIVATE_WITH_EGRESS)))
        .build();
  }

  private static SubnetConfiguration subnet(String name, SubnetType type) {
    return SubnetConfiguration.builder().name(name).subnetType(type).cidrMask(24).build();
  }

  /**
   * No allow-all outbound: every outbound rule is added explicitly below.
   *
   * @param constructId {@link String} CDK construct ID. Changing it replaces the security group.
   * @param label {@link String}
   */
  private SecurityGroup createSecurityGroup(String constructId, String label) {
    return SecurityGroup.Builder.create(this, constructId)
        .vpc(vpc)
        .securityGroupName("book-library-" + label.toLowerCase(Locale.ROOT) + "-sg")
        .description("Security group for Book Library " + label + " function")
        .allowAllOutbound(false)
        .build();
  }

  /** Lambda -> RDS on 5432: outbound from Lambda, and the only inbound RDS accepts. */
  private void allowLambdaToReachRds() {
    lambdaSecurityGroup.addEgressRule(
        rdsSecurityGroup, Port.tcp(POSTGRES_PORT), "Allow lambda to connect to RDS PostgreSQL");
    rdsSecurityGroup.addIngressRule(
        lambdaSecurityGroup,
        Port.tcp(POSTGRES_PORT),
        "Allow inbound PostgreSQL from Lambda security group only");
  }

  /** Lambda -> AWS services over HTTPS (Secrets Manager, AppConfig, CloudWatch). */
  private void allowLambdaToReachAwsServices() {
    lambdaSecurityGroup.addEgressRule(Peer.anyIpv4(), Port.tcp(HTTPS_PORT));
  }
}
