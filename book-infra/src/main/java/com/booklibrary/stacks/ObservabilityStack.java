package com.booklibrary.stacks;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.cloudtrail.Trail;
import software.amazon.awscdk.services.cloudwatch.*;
import software.amazon.awscdk.services.cloudwatch.actions.SnsAction;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.logs.*;
import software.amazon.awscdk.services.sns.*;
import software.constructs.Construct;

import java.util.List;

/** Lambda log group, email alarms, a CloudWatch dashboard and a CloudTrail audit trail. */
public class ObservabilityStack extends Stack {

  /**
   * CDK context key for the email address that receives alarm notifications. Set it in {@code
   * cdk.json} under {@code context.alertEmail}, or pass it on the CLI with {@code -c
   * alertEmail=you@example.com}. If unset, the SNS topic is still created but no email
   * subscription is added.
   */
  private static final String ALERT_EMAIL_CONTEXT_KEY = "alertEmail";

  private final Topic alarmTopic;

  public ObservabilityStack(
      Construct scope, String id, StackProps props, ComputeStack computeStack, ApiStack apiStack) {
    super(scope, id, props);

    Function lambda = computeStack.getBookApiLambda();
    Metric invocations = lambda.metricInvocations(metricOptions());
    Metric errors = lambda.metricErrors(metricOptions());
    Metric duration = lambda.metricDuration(metricOptions());

    createLambdaLogGroup();
    this.alarmTopic = createAlarmTopic();

    createErrorAlarm(errors);
    createDurationAlarm(duration);
    createDashboard(invocations, errors, duration);
    createAuditTrail();
  }

  private static MetricOptions metricOptions() {
    return MetricOptions.builder().period(Duration.minutes(5)).build();
  }

  /** Lambda logs, kept for 1 month. */
  private void createLambdaLogGroup() {
    LogGroup.Builder.create(this, "LambdaLogGroup")
        .logGroupName("/aws/lambda/book-library-api")
        .retention(RetentionDays.ONE_MONTH)
        .build();
  }

  /**
   * Receives alarm notifications and forwards them by email when an address is configured.
   *
   * @return Alarm Topic {@link Topic}
   */
  private Topic createAlarmTopic() {
    Topic topic =
        Topic.Builder.create(this, "AlarmTopic")
            .topicName("book-library-alarms")
            .displayName("Book Library Alarm Notifications")
            .build();

    Object alertEmailCtx = this.getNode().tryGetContext(ALERT_EMAIL_CONTEXT_KEY);
    if (alertEmailCtx != null && !alertEmailCtx.toString().isBlank()) {
      Subscription.Builder.create(this, "AlarmEmailSubscription")
          .topic(topic)
          .protocol(SubscriptionProtocol.EMAIL)
          .endpoint(alertEmailCtx.toString())
          .build();
    } else {
      Annotations.of(this)
          .addWarning(
              "No '"
                  + ALERT_EMAIL_CONTEXT_KEY
                  + "' CDK context value set — alarms will publish to SNS but no email"
                  + " subscription will be created. Pass `-c "
                  + ALERT_EMAIL_CONTEXT_KEY
                  + "=you@example.com` or set it in cdk.json to enable email alerts.");
    }

    return topic;
  }

  /**
   * Alarm on any Lambda error in a 5-minute window.
   *
   * @param errors {@link Metric}
   */
  private void createErrorAlarm(Metric errors) {
    createAlarm(
        "LambdaErrorAlarm",
        "book-library-lambda-errors",
        "Lambda threw an error — check logs",
        errors,
        1,
        1,
        ComparisonOperator.GREATER_THAN_OR_EQUAL_TO_THRESHOLD);
  }

  /**
   * Alarm on any Lambda error in a 5-minute window.
   *
   * @param duration {@link Metric}
   */
  private void createDurationAlarm(Metric duration) {
    createAlarm(
        "LambdaDurationAlarm",
        "book-library-lambda-duration",
        "Lambda duration exceeded 10s — possible cold start or slow query",
        duration,
        10000,
        2,
        ComparisonOperator.GREATER_THAN_THRESHOLD);
  }

  /**
   * Creates an alarm that notifies the alarm topic. Missing data does not count as a breach.
   *
   * @param constructId {@link String}
   * @param alarmName {@link String}
   * @param description {@link String}
   * @param metric {@link Metric}
   * @param threshold {@link Integer}
   * @param evaluationPeriods {@link Integer}
   * @param comparisonOperator {@link ComparisonOperator}
   */
  private void createAlarm(
      String constructId,
      String alarmName,
      String description,
      Metric metric,
      int threshold,
      int evaluationPeriods,
      ComparisonOperator comparisonOperator) {
    Alarm alarm =
        Alarm.Builder.create(this, constructId)
            .alarmName(alarmName)
            .alarmDescription(description)
            .metric(metric)
            .threshold(threshold)
            .evaluationPeriods(evaluationPeriods)
            .comparisonOperator(comparisonOperator)
            .treatMissingData(TreatMissingData.NOT_BREACHING)
            .build();

    alarm.addAlarmAction(new SnsAction(alarmTopic));
  }

  /**
   * Single-pane view of Lambda metrics
   *
   * @param invocations {@link Metric}
   * @param errors {@link Metric}
   * @param duration {@link Metric}
   */
  private void createDashboard(Metric invocations, Metric errors, Metric duration) {
    Dashboard dashboard =
        Dashboard.Builder.create(this, "BookLibraryDashboard")
            .dashboardName("book-library-dashboard")
            .build();

    dashboard.addWidgets(
        GraphWidget.Builder.create()
            .title("Lambda Invocations vs Errors")
            .left(List.of(invocations))
            .right(List.of(errors))
            .width(12)
            .build(),
        GraphWidget.Builder.create()
            .title("Lambda Duration (ms)")
            .left(List.of(duration))
            .width(12)
            .build());
  }

  /** Audit AWS API calls in this account and region. */
  private void createAuditTrail() {
    Trail.Builder.create(this, "BookLibraryTrail")
        .trailName("book-library-audit-trail")
        .isMultiRegionTrail(false)
        .includeGlobalServiceEvents(true)
        .enableFileValidation(true)
        .build();
  }
}
