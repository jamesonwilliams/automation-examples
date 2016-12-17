#!/bin/bash
#
# Copyright 2016 Amazon.com, Inc. or its affiliates. All Rights
# Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License").  You
# may not use this file except in compliance with the License.  A copy
# of the License is located at
#
#    http://aws.amazon.com/apache2.0/
#
# or in the "license" file accompanying this file.  This file is
# distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
# ANY KIND, either express or implied.  See the License for the specific
# language governing permissions and limitations under the License.

#
# This script used the AWS cli to interact with the EC2 Systems Manager
# automation service. The example shows how to create a new linux ami
# using the AWS-UpdateLinuxAmi public automation document.
#

set -e

# The public automation document that will be run.
readonly AUTOMATION_DOCUMENT="AWS-UpdateLinuxAmi"

# The frequency with which to poll for task completion.
readonly POLL_PERIOD_SECONDS="10"

AUTOMATION_ASSUME_ROLE=""
SOURCE_AMI_ID=""
INSTANCE_IAM_ROLE=""

EXECUTION_ID=""

#
# Kill the program and provide an error message in $1.
die() {
    echo "$@" 1>&2
    exit 1
}

#
# Start the creation of a new linux AMI, by making a call to the
# Automation service.
create_linux_ami() {
    aws ssm start-automation-execution \
        --document-name "$AUTOMATION_DOCUMENT" \
        --parameters "AutomationAssumeRole=$AUTOMATION_ASSUME_ROLE,
                      SourceAmiId=$SOURCE_AMI_ID,
                      InstanceIamRole=$INSTANCE_IAM_ROLE" | \
    jq -r '.AutomationExecutionId'
}

#
# Get the ID of the created AMI, if the execution has completed
# successfully.
get_result() {
    aws ssm get-automation-execution \
        --automation-execution-id "$EXECUTION_ID" | \
    jq -r '.AutomationExecution.Outputs["createImage.ImageId"][0]'
}

#
# Get the current status of the automation execution.
get_status() {
    aws ssm get-automation-execution \
        --automation-execution-id "$EXECUTION_ID" | \
    jq -r '.AutomationExecution.AutomationExecutionStatus'
}

#
# If the execution has failed, get a message indicating what went wrong.
get_failure_message() {
    aws ssm get-automation-execution \
        --automation-execution-id "$EXECUTION_ID" | \
    jq -r '.AutomationExecution.FailureMessage'
}

#
# Check to see if the execution's status is a final one, or if the
# automation is still pending or in progress.
is_execution_finished() {
    status="$1"

    case "$status" in
        "InProgress" | "Pending")
            echo "false"
            ;;
        *)
            echo "true"
            ;;
    esac
}

#
# Poll the Auomation service for job completion, and print the result of
# the automation if successful. If it failed, print the reason for the
# failure.
wait_for_result() {
    status="$(get_status $EXECUTION_ID)"

    while [ "false" == "$(is_execution_finished $status)" ]; do
        status="$(get_status $EXECUTION_ID)"
        echo "Execution status is $status."
        sleep "$POLL_PERIOD_SECONDS"
    done

    case "$status" in
        "Success")
            get_result
        ;;
        "Failed" | "TimedOut")
            get_failure_message
        ;;
    esac
}

#
# Obtained the required automation parameters as inputs from the command
# line.
parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            --automation-assume-role)
                AUTOMATION_ASSUME_ROLE="$2"
                ;;
            --instance-iam-role)
                INSTANCE_IAM_ROLE="$2"
                ;;
            --source-ami-id)
                SOURCE_AMI_ID="$2"
                ;;
            *)
                die "Unkown argument: $1"
                ;;
        esac

        shift 2
    done

    if [ -z "$AUTOMATION_ASSUME_ROLE" ]; then
        die "Did not specify required parameter --automation-assume-role"
    elif [ -z "$INSTANCE_IAM_ROLE" ]; then
        die "Did not specify required parameter --instance-iam-role"
    elif [ -z "$SOURCE_AMI_ID" ]; then
        die "Did not specify required parameter --source-ami-id"
    fi
}

main() {
    parse_args "$@"

    EXECUTION_ID="$(create_linux_ami)"

    wait_for_result
}

main "$@"
