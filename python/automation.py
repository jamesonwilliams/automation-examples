#!/usr/bin/env python
#
# Exapmle of using the boto3 library to interact with EC2 Systems
# Manager Automation.
#
# This code will Automation to apply OS updates to a Linux AMI, creating
# a new one.
#
# See the boto3 documentation for these APIs at
# https://boto3.readthedocs.io/en/latest/reference/services/ssm.html#client
#

import boto3
import time

# The machine image we are going to update, Ubuntu 14.04 LTS.
source_ami='ami-5ac2cd4d'

# The role in the user's account that the Automation service will
# assume (must have a policy setup to allow this.)
assume_role='arn:aws:iam::237436237222:role/MyAutomationRole'

# The name of the role to attach to the temporary instance used when
# applying operating system updates.
instance_profile_name='MyInstanceRole'

# The input parametres for the automation execution.
parameters = {
    'AutomationAssumeRole' : [ assume_role ],
    'InstanceIamRole' : [ instance_profile_name ],
    'SourceAmiId' : [ source_ami ]
}

# The automation document that will be used.
document_name='AWS-UpdateLinuxAmi'

client = boto3.client('ssm')
start_execution_response = client.start_automation_execution(
    DocumentName=document_name,
    Parameters=parameters
)

execution_id = start_execution_response['AutomationExecutionId']

status = 'Pending'

while status == 'Pending' or status == 'InProgress':
    get_execution_response = client.get_automation_execution(
        AutomationExecutionId=execution_id
    )

    execution = get_execution_response['AutomationExecution']

    status = execution['AutomationExecutionStatus']
    print "Execution is", status

    if status == 'Completed':
        ami_id = execution['Outputs']['createImage.ImageId']
        print 'AMI id is ', ami_id
    elif status == 'Failed' or status == 'TimedOut':
        print execution['FailureMessage']
    else:
        time.sleep(5)

