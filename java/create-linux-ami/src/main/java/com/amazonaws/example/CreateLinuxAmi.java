/**
 * Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License").  You
 * may not use this file except in compliance with the License.  A copy
 * of the License is located at
 *
 *   http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file.  This file is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under the
 * License.
 */
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

    /**
     * See -h for command line options.
     */
    public static void main(String[] args) throws ParseException {
        CommandLine line = parseArguments(args);

        final String executionId = createLinuxAmi(
            line.buildOptionValue("automation-assume-role"),
            line.buildOptionValue("instance-iam-role"),
            line.buildOptionValue("source-ami-id")
        );

        waitForResult(executionId);
    }

    /**
     * Invoke the SSM Automation service to automate the creation of a
     * new Linux AMI.
     *
     * @param automationAssumeRole the ARN of the role the service will
     *                             assume while performing actions on
     *                             the user's behalf.
     * @param instanceIamRole the name of the IAM role that should be
     *                        associated with the temporary ec2 instance
     *                        that is used for this automation.
     * @param sourceAmiId the id of the AMI to use as a basis for
     *                    updating.
     *
     * @return the id of the newly started automation job.
     */
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

    /**
     * Wait for the result of the automation to be ready, and then
     * display it.
     *
     * If the execution fails, then print the reason for the failure.
     *
     * @param executionId the id of the execution to monitor.
     */
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

    /**
     * Check if an automation execution is finished.
     *
     * @param execution the automation execution to check for completion
     *
     * @return true if the execution is neither pending or in progress;
     *         false, if it is.
     */
    private static boolean isFinished(final AutomationExecution execution) {
        final AutomationExecutionStatus status = AutomationExecutionStatus.valueOf(
            execution.getAutomationExecutionStatus()
        );

        return !AutomationExecutionStatus.InProgress.equals(status) &&
            !AutomationExecutionStatus.Pending.equals(status);
    }

    /**
     * Pause the thread of execution for a specified number of
     * milliseconds.
     *
     * @param durationMs the number of milliseconds to sleep.
     */
    private static void sleep(final int durationMs) {
        try {
            Thread.sleep(durationMs);
        } catch (InterruptedException interruptedException) {
            throw new RuntimeException("Thread failed to sleep, exiting.");
        }
    }

    /**
     * Get a handle the AWS SSM client.
     *
     * @return a handle the SSM client.
     */
    private static AWSSimpleSystemsManagement getClient() {
        AWSSimpleSystemsManagement client =
            new AWSSimpleSystemsManagementClient(getCredentials());

        Region usEast1 = Region.getRegion(Regions.US_EAST_1);
        client.setRegion(usEast1);

        return client;
    }

    /**
     * Get the credentials of the user executing this program.
     *
     * These are pulled from ~/.aws/credentials.
     *
     * @return an AWS credentials object
     */
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

    /**
     * Parse the command line arguments into a command line object.
     *
     * @param args the string array passed into the main method
     *
     * @return a CommandLine object representing the passed options.
     */
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

    /**
     * Build a command line option object.
     *
     * @param key the command line option name
     * @param description a description of what this option does
     * @param argName the argument value that is associated with the
     *                 option value; e.g. --foo=argName.
     *
     * @return a new Option object.
     */
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

