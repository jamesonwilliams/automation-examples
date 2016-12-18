#!/usr/bin/env ruby
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
# Example of using the AWS Ruby SDK to interact with EC2 Systems Manager
# Automation.
#
# See https://docs.aws.amazon.com/sdkforruby/api/Aws/SSM/Client.html.
#
# This code will automate the process of applying OS updates to a Linux
# AMI, creating a new one.
#

require 'aws-sdk'
require 'optparse'

# Our client handle to the Simple Systems Manager service.
SSM = Aws::SSM::Client.new(region: 'us-east-1')

# The public automation document that will be executed.
DOCUMENT_NAME = 'AWS-UpdateLinuxAmi'

# Possible state outcomes of an automation execution.
FINAL_STATES = ['Cancelled', 'Success', 'Failed', 'TimedOut']

# The frequency with which to poll the service for job completion.
POLL_PERIOD_SECONDS = 10

#
# Creates a new Linux AMI.
# 
#   automation_assume_role - The role in the user's account that the
#                            Automation service will assume.
#                            
#   instance_iam_role      - The name of the role to attach to the
#                            temporary instance used when applying
#                            operating system updates. 
#                            
#   source_ami_id          - The machine image we are going to update.
#
# Returns a unique id for the ongoing automation job.
# 
def create_linux_ami(automation_assume_role, instance_iam_role, source_ami_id)
  SSM.start_automation_execution({
    document_name: DOCUMENT_NAME,
    parameters: {
      "AutomationAssumeRole" => [automation_assume_role],
      "InstanceIamRole" => [instance_iam_role],
      "SourceAmiId" => [source_ami_id]
    },
  }).automation_execution_id
end

#
# Wait for the result of the create ami task, and print the result.
#
#   execution_id - the job id of the started execution
#
def wait_for_result(execution_id)
  status = ""
  execution = ""
  
  until FINAL_STATES.include?(status)
    execution = SSM.get_automation_execution({
      automation_execution_id: execution_id,
    }).automation_execution
    
    status = execution.automation_execution_status
  
    sleep(POLL_PERIOD_SECONDS)
  
    puts "Automation status is #{status}"
  end
  
  case status
  when 'Failed', 'TimedOut'
    puts execution.failure_message   
  when 'Success'
    puts execution.outputs['createImage.ImageId']
  else
    puts "Unexpected execution status #{status}"
  end
end


#
# Parse the required execution parameters from the command line
# arguments.
#
# Throw an exception if any are missing.
#
# Return them as a hash.
def parse_arguments()
  ARGV << '-h' if ARGV.empty?

  options = {}

  OptionParser.new do |opt|
    opt.on('--automation-assume-role AUTOMATION_ASSUME_ROLE') {
      |o| options[:automation_assume_role] = o
    }
    opt.on('--instance-iam-role INSTANCE_IAM_ROLE') {
      |o| options[:instance_iam_role] = o
    }
    opt.on('--source-ami-id SOURCE_AMI_ID') {
      |o| options[:source_ami_id] = o
    }
  end.parse!

  for item in ['automation_assume_role', 'instance_iam_role', 'source_ami_id']
    if not options.key?(item.to_sym)
      raise OptionParser::MissingArgument.new(item)
    end
  end

  return options
end

if __FILE__ == $PROGRAM_NAME
  options = parse_arguments()

  execution_id = create_linux_ami(
    options[:automation_assume_role],
    options[:instance_iam_role],
    options[:source_ami_id]
  )

  wait_for_result(execution_id)
end

