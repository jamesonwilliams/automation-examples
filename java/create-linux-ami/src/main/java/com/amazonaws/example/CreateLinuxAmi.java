package com.amazonaws.example;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.model.AutomationExecution;
import com.amazonaws.services.simplesystemsmanagement.model.AutomationExecutionStatus;
import com.amazonaws.services.simplesystemsmanagement.model.GetAutomationExecutionRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetAutomationExecutionResult;
import com.amazonaws.services.simplesystemsmanagement.model.StartAutomationExecutionRequest;
import com.amazonaws.services.simplesystemsmanagement.model.StartAutomationExecutionResult;

import com.google.common.collect.ImmutableMap;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Java example of using EC2 Systems Manager Automation to create a
 * Linux AMI.
 */
public class CreateLinuxAmi {
    private static final String DOCUMENT_NAME = "AWS-UpdateLinuxAmi";
    private static final String OUTPUT_KEY = "createImage.ImageId";
    private static final int POLL_PERIOD_MS = 30 * 1000;

    private static final AWSSimpleSystemsManagement SSM = getClient();

    public static void main(String[] args) throws ParseException {
        CommandLine line = parseArguments(args);

        final String executionId = createLinuxAmi(
            line.buildOptionValue("automation-assume-role"),
            line.buildOptionValue("instance-iam-role"),
            line.buildOptionValue("source-ami-id")
        );

        waitForResult(executionId);
    }

    private static String createLinuxAmi(
            final String automationAssumeRole,
            final String instanceIamRole,
            final String sourceAmiId) {

        final Map<String, List<String>> parameters = ImmutableMap.of(
            "AutomationAssumeRole", Arrays.asList(automationAssumeRole),
            "InstanceIamRole", Arrays.asList(instanceIamRole),
            "SourceAmiId", Arrays.asList(sourceAmiId)
        );

        final StartAutomationExecutionRequest request =
            new StartAutomationExecutionRequest();

        request.setParameters(parameters);
        request.setDocumentName(DOCUMENT_NAME);

        final StartAutomationExecutionResult result =
            SSM.startAutomationExecution(request);

        return result.getAutomationExecutionId();
    }

    private static void waitForResult(final String executionId) {

        final GetAutomationExecutionRequest request =
            new GetAutomationExecutionRequest();

        request.withAutomationExecutionId(executionId);

        AutomationExecution execution;
        AutomationExecutionStatus status;

        do {
            execution = SSM.getAutomationExecution(request)
                           .getAutomationExecution();

            status = AutomationExecutionStatus.valueOf(
                execution.getAutomationExecutionStatus());
            System.out.println("Status is "  + status);

            sleep(POLL_PERIOD_MS);

        } while (!isFinished(execution));

        if (AutomationExecutionStatus.Success.equals(status)) {
            final String amiId = execution.getOutputs().get(OUTPUT_KEY).get(0);
            System.out.println("New AMI ID is " + amiId);
        } else {
            throw new RuntimeException(
                "Automation execution failed with message" 
                + execution.getFailureMessage()
            );
        }
    }

    private static boolean isFinished(final AutomationExecution execution) {
        final AutomationExecutionStatus status = AutomationExecutionStatus.valueOf(
            execution.getAutomationExecutionStatus()
        );

        return !AutomationExecutionStatus.InProgress.equals(status) &&
            !AutomationExecutionStatus.Pending.equals(status);
    }

    private static void sleep(final int durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException interruptedException) {
            throw new RuntimeException("Thread failed to sleep, exiting.");
        }
    }

    private static AWSSimpleSystemsManagement getClient() {
        AWSSimpleSystemsManagement client =
            new AWSSimpleSystemsManagementClient(getCredentials());

        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        client.setRegion(usEast1);

        return client;
    }

    private static AWSCredentials getCredentials() {
        try {
            return new ProfileCredentialsProvider().getCredentials();
        } catch (Exception exception) {
            throw new AmazonClientException(
                "Cannot load the credentials from the credential profiles file. " +
                "Please make sure that your credentials file is at the correct " +
                "location (~/.aws/credentials), and is in valid format.",
                exception
            );
        }
    }

    private static CommandLine parseArguments(String[] args)
            throws ParseException {

        CommandLineParser parser = new DefaultParser();
        
        Options options = new Options();
        options.addOption(buildOption(
            "automation-assume-role",
            "The role under which to execute the automation",
            "ASSUME_ROLE"
        ));
        options.addOption(buildOption(
            "source-ami-id",
            "The ID of the AMI to use as a basis for updates",
            "AMI_ID"
        ));
        options.addOption(buildOption(
            "instance-iam-role",
            "The ID of the AMI to use as a basis for updates",
            "INSTANCE_ROLE_NAME"
        ));
        
        return parser.parse(options, args);
    }

    private static Option buildOption(final String key,
            final String description, final String argName) {

        return OptionBuilder
            .withLongOpt(key)
            .withDescription(description)
            .hasArg()
            .isRequired()
            .withArgName(argName)
            .create();
    }
}

