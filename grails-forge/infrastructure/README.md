<!--
SPDX-License-Identifier: Apache-2.0

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->

# Grails Forge AWS Infrastructure

Deploy `shared.yaml` first and then one `environment.yaml` stack for each slot. The shared stack owns the artifact bucket, public ALB, Elastic Beanstalk application, IAM roles, and security groups. Each slot stack owns exactly one Elastic Beanstalk environment and one unique HTTPS listener-rule priority.

The current target is `us-east-1` in the default VPC. Discover its ID and public default subnets before deploying. Supply at least two subnet IDs from different Availability Zones to `PublicSubnets` and `InstanceSubnets`.

```bash
VPC_ID=$(aws ec2 describe-vpcs \
  --region us-east-1 \
  --filters Name=is-default,Values=true \
  --query 'Vpcs[0].VpcId' \
  --output text)

aws ec2 describe-subnets \
  --region us-east-1 \
  --filters Name=vpc-id,Values="$VPC_ID" Name=default-for-az,Values=true \
  --query 'Subnets[].SubnetId' \
  --output text
```

Use a concrete Corretto 25 Elastic Beanstalk platform ARN. Grails 7 and Grails 8 both run on Java 25, so this avoids older Corretto platform deprecations. Use `Architecture=arm64` and `InstanceType=t4g.small` when the selected platform advertises arm64. Otherwise, use `Architecture=x86_64` and `InstanceType=t3.small` with a matching x86_64 platform ARN.

```bash
aws cloudformation deploy \
  --region us-east-1 \
  --stack-name <shared-stack-name> \
  --template-file shared.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    VpcId=<vpc-id> \
    PublicSubnets='<subnet-id-1>,<subnet-id-2>' \
    CertificateArn=<acm-certificate-arn> \
    GitHubOidcProviderArn=<github-oidc-provider-arn>
```

Deploy five environment stacks with distinct `Slot`, `HostName`, and `ListenerRulePriority` values. Every listener priority must be unique in the range 1-50000. The command below deploys the `latest` slot.

```bash
aws cloudformation deploy \
  --region us-east-1 \
  --stack-name <environment-stack-name> \
  --template-file environment.yaml \
  --parameter-overrides \
    SharedStackName=<shared-stack-name> \
    Slot=latest \
    HostName=latest.grails.org \
    ListenerRulePriority=10 \
    InstanceSubnets='<subnet-id-1>,<subnet-id-2>' \
    PlatformArn=<corretto-25-platform-arn> \
    Architecture=arm64 \
    InstanceType=t4g.small \
    CorsAllowedOrigin=https://start.grails.org
```

Repeat the environment deployment for `snapshot`, `next`, `prev`, and `prev-snapshot`, supplying each existing `*.grails.org` hostname and a unique priority. `CorsAllowedOrigin` is the browser UI origin and is intentionally independent of the API slot hostname.

Do not create a GitHub OAuth app secret or pass OAuth client credentials to these stacks. The start.grails.org UI removed Push to GitHub, and the unused server-side create/OAuth integration is not deployed. Analytics is also omitted.

Cloudflare DNS-only CNAME records for `latest.grails.org`, `snapshot.grails.org`, `next.grails.org`, `prev.grails.org`, and `prev-snapshot.grails.org` already point at the shared ALB. Do not proxy those records. Do not create or modify `start.grails.org`.

```bash
aws cloudformation list-exports \
  --region us-east-1 \
  --query "Exports[?Name=='<shared-stack-name>:SharedLoadBalancerDnsName'].Value" \
  --output text
```

`dns.yaml` is optional future Route 53 support only. Do not deploy it while Cloudflare remains authoritative. If the hosted zone later moves to Route 53, deploy the template after the five environments are healthy.

```bash
aws cloudformation deploy \
  --region us-east-1 \
  --stack-name <dns-stack-name> \
  --template-file dns.yaml \
  --parameter-overrides \
    SharedStackName=<shared-stack-name> \
    HostedZoneId=<route53-hosted-zone-id>
```

GitHub Actions assumes the shared stack's `DeployRoleArn`. Upload a normal JAR deployment ZIP to the exported artifact bucket, create an Elastic Beanstalk application version, then update one exported environment name. The trust policy allows `apache/grails-core` maintenance branches matching `refs/heads/*.x` and tags matching `refs/tags/v*`. The deploy policy is restricted to the application, its versions, and the five declared slot environment names.
