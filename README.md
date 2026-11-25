# AWS CDK in Practice: A Serverless Stack, Built Entirely in Java

A small CRUD REST API for Book Library, built end-to-end in **Java**:

- **Infrastructure**: AWS CDK in Java.
- **Application**: Spring Boot on Java, running on AWS Lambda.
- **API Gateway**: Generated from the application's own OpenAPI spec.
- **Database**: Amazon RDS for PostgreSQL
- **Feature Flags**: via AWS AppConfig
- **Observability**: CLoudWatch dashboard, alarms on SNS, CloudTrail audit.


## Architecture

![Architecture](docs/Java%20CDK.drawio.png)

## Request Flow

![Request sequence](docs/AWS%20CDK%20Sequence%20Diagram.drawio.png)

1. The client sends an HTTPS request to API Gateway.
2. API Gateway validates the body against a schema generated from the Spring controllers.
3. Lambda handles the request inside a private subnet.
4. The app reads database credentials from Secrets Manager (cold start only) and feature flags from AppConfig (cached locally).
5. JPA / PostgreSQL does the data work.
6. Logs and metrics land in CloudWatch.


## The five stacks

![CDK stacks](docs/CDK%20Stacks.drawio.png)

Stacks are split by **rate of change**, not by service:

| Stack | Contents | Changes… |
|---|---|---|
| `NetworkStack` | VPC, subnets, security groups | rarely |
| `DatabaseStack` | KMS key, Secrets Manager secret, RDS Postgres | rarely |
| `ComputeStack` | Lambda, IAM role, artifact bucket, AppConfig | with every app release |
| `ApiStack` | API Gateway (from the OpenAPI spec) | with every API change |
| `ObservabilityStack` | Log groups, alarms, dashboard, CloudTrail | often |

### How a deploy works

![CDK deployment flow](docs/AWS%20CDK%20Deployment%20Flow.drawio.png)

The Java CDK app synthesizes to CloudFormation templates. `cdk deploy` hands those to CloudFormation, which creates the stacks in dependency order: Network → Database → Compute → API & Observability.

## Instructions
Follow the below instructions in order to run this project.

### Prerequisites
- **Java 21** (`java -version` should show 21.x)
- **Maven 3.8+**
- **AWS CLI**, configured with credentials (`aws sts get-caller-identity` should work)
- **AWS CDK CLI** — `npm install -g aws-cdk`
- An AWS account **bootstrapped for CDK** in your target region:
  ```bash
  cdk bootstrap aws://<ACCOUNT_ID>/<REGION>
  ```

### Build and Deploy

The CDK stack reads the Lambda JAR from `book-api/target/`, so build the application first.


#### 1. Build the application

```bash
cd book-api
mvn clean package
```

This produces:
- `target/book-api-1.0.0.jar` — the Lambda deployment artifact
- `target/openapi.json` — the OpenAPI spec used by `ApiStack` to define API Gateway

#### 2. Deploy the infrastructure

```bash
cd ../book-infra
cdk deploy --all -c alertEmail=you@example.com
```

The `-c alertEmail=...` sets the email address that receives CloudWatch alarm notifications via SNS. If you omit it, alarms still fire to the SNS topic but no email subscription is created (CDK will print a warning).

CDK will synthesize the stacks and prompt for IAM/security-group changes. Confirm to proceed. Deploy time is ~8–12 minutes on a fresh account (RDS is the slow part).

Outputs include the API Gateway URL — save it, you'll use it below.

## Testing the API
### 1. Export the API definition

Find your API ID and stage name in the API Gateway console, or in the outputs of the `ApiStack` deployment. Then export the definition with the AWS CLI:

``` bash
aws apigateway get-export \
  --rest-api-id <api-id> \
  --stage-name <stage-name> \
  --export-type oas30 \
  --parameters extensions=postman \
  api-definition.json
```

You can also export it from the console: **API Gateway → your API → Stages → select the stage → Export**, then choose **OpenAPI 3** and the **Postman** extensions option.

### 2. Import it into Postman

1. In Postman, click **Import**.
2. Drag in `api-definition.json`, or click **Files** and select it.
3. When prompted, choose **Postman Collection** as the import type and confirm.

Postman creates a collection with one request for each endpoint and method.

### 3. Set the base URL

The invoke URL for your stage follows this pattern:

```
https://<api-id>.execute-api.<region>.amazonaws.com/<stage-name>
```

If the collection uses a `baseUrl` variable, set it to this value under the collection's **Variables** tab. Otherwise, update the host in each request.

## Tearing it down

```bash
cd book-infra
cdk destroy --all
```

The project uses `RemovalPolicy.DESTROY` and `deletionProtection(false)` so everything is removed cleanly — including the RDS instance. Suitable for a demo, **not** for production.


## Further reading

## Known limitations

This is a portfolio project, not a production system. Known gaps:

- **Cold starts.** A JVM Lambda in a VPC starts in several seconds. Lambda SnapStart or GraalVM native image would help.
- **NAT Gateway cost.** The NAT dominates the bill for a small project. VPC endpoints for Secrets Manager and AppConfig would replace it.
- **No RDS Proxy.** Burst invocations can exhaust Postgres connections.
- **No CI/CD.** Deploys are `cdk deploy` from a developer laptop.
- **No API auth.** The API is open. For anything real, add Cognito or an API key authorizer.

---

## License

Released under the [MIT License](LICENSE).
