# automation-examples
Example client code for interacting the EC2 Systems Manager Automation Service.

## Python
Requires `boto3`:
```
pip install boto3
```

Run the example like:

```
./create-linux-ami.py \
    --automation-assume-role 'arn:aws:iam::837426271803:role/AutomationRole' \
    --instance-iam-role 'MyInstanceRole' \
    --source-ami-id 'ami-5ac2cd4d'
```
