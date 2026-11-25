package com.booklibrary;

import com.booklibrary.stacks.*;
import com.booklibrary.stacks.NetworkStack;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.Tags;

public class BookLibraryApp {

  public static void main(String[] args) {

    App app = new App();

    Environment environment =
        Environment.builder()
            .account(System.getenv("CDK_DEFAULT_ACCOUNT"))
            .region(System.getenv("SDK_DEFAULT_REGION"))
            .build();

    StackProps stackProps = StackProps.builder().env(environment).build();

    // Create Stacks in dependency order
    NetworkStack networkStack = new NetworkStack(app, "BookLibraryNetworkStack", stackProps);
    DatabaseStack databaseStack =
        new DatabaseStack(app, "BookLibraryDatabaseStack", stackProps, networkStack);
    ComputeStack computeStack =
        new ComputeStack(app, "BookLibraryComputeStack", stackProps, networkStack, databaseStack);
    ApiStack apiStack = new ApiStack(app, "BookLibraryApiStack", stackProps, computeStack);
    new ObservabilityStack(
        app, "BookLibraryObservabilityStack", stackProps, computeStack, apiStack);

    // Add tags to every resource in all the stacks
    Tags.of(app).add("Project", "aws-book-library");
    Tags.of(app).add("Environment", "dev");
    Tags.of(app).add("ManagedBy", "CDK");

    app.synth();
  }
}
