#!/usr/bin/env python
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
# Exapmle of using the boto3 library to interact with EC2 Systems
# Manager Automation.
#
# This code will automate the process of applying OS updates to a Linux
# AMI, creating a new one.
#
# See the boto3 documentation for these APIs at
# https://boto3.readthedocs.io/en/latest/reference/services/ssm.html#client
#

import argparse
import boto3
import time

# The automation document that will be used.
AUTOMATION_DOCUMENT_NAME='AWS-UpdateLinuxAmi'

# The frequency to poll the service for job completion
POLL_PERIOD_SECONDS=60

# Our client handle to the Simple Systems Manager service.
CLIENT = boto3.client('ssm')

#
# Creates a new Linux AMI.
# 
#   automation_assume_role - The role in the user's account that the
#                            Automation service will assume.
#                            
#   instance_profile_name  - The name of the role to attach to the
#                            temporary instance used when applying
#                            operating system updates. 
#                            
#   source_ami_id          - The machine image we are going to update.
#
# Returns a unique id for the ongoing automation job.
# 
def create_new_linux_ami(automation_assume_role, instance_iam_role, source_ami_id):
    parameters = {
        'AutomationAssumeRole' : [ automation_assume_role ],
        'InstanceIamRole' : [ instance_iam_role ],
        'SourceAmiId' : [ source_ami_id ]
    }
    
    start_execution_response = CLIENT.start_automation_execution(
        DocumentName=AUTOMATION_DOCUMENT_NAME,
        Parameters=parameters
    )
    
    return start_execution_response['AutomationExecutionId']

# 
# Whether or not the execution is still going.
# 
#   status - the current status of the execution
#
# Returns true if the execution is still happening; false, otherwise.
# 
def execution_not_ended(status):
    return status == 'InProgress' or status == 'Pending'

#
# Wait for the result of the create ami task, and print the result.
#
#   automation_execution_id - the job id of the started execution
#
def wait_for_result(automation_execution_id):
    status = 'Pending'
    
    while execution_not_ended(status):
        get_execution_response = CLIENT.get_automation_execution(
            AutomationExecutionId=automation_execution_id
        )
    
        execution = get_execution_response['AutomationExecution']
        status = execution['AutomationExecutionStatus']

        print "Execution status is:", status

        if execution_not_ended(status):
            time.sleep(POLL_PERIOD_SECONDS)
    
    if status == 'Success':
        print 'AMI id is', execution['Outputs']['createImage.ImageId']
    else:
        print execution['FailureMessage']
 
def parse_arguments():
    parser = argparse.ArgumentParser(
        description="Update a Linux AMI using EC2 Systems Manager"
    )
    parser.add_argument(
        "--automation-assume-role",
        help="The role under which to execute the automation",
        dest="automation_assume_role",
        metavar='AUTOMATION_ROLE_ARN',
        required=True
    )
    parser.add_argument(
        "--instance-iam-role",
        help="The name of the IAM role to attach to the workspace instance",
        dest="instance_iam_role",
        metavar='INSTANCE_ROLE_NAME',
        required=True
    )
    parser.add_argument(
        "--source-ami-id",
        help="The ID of the AMI to use as a basis for updates",
        dest="source_ami_id",
        metavar="AMI_ID",
        required=True
    )

    return parser.parse_args()

if __name__ == "__main__":
    opts = parse_arguments()

    job_id = create_new_linux_ami(
        opts.automation_assume_role,
        opts.instance_iam_role,
        opts.source_ami_id
    )

    wait_for_result(job_id)

