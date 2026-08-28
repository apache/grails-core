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

# AWS Elastic Beanstalk Deployment Runbook

This runbook describes operating the five Forge API slots on AWS Elastic Beanstalk. Public DNS for `latest.grails.org`, `snapshot.grails.org`, `next.grails.org`, `prev.grails.org`, and `prev-snapshot.grails.org` points at the shared ALB. The UI remains at `https://start.grails.org`.

## Architecture

Five Forge API slots run in separate Elastic Beanstalk environments. One shared application load balancer (ALB) terminates TLS and routes each stable hostname by host header. The Forge UI remains at `https://start.grails.org` and is not part of this migration.

The shared CloudFormation stack creates the ALB and listeners, Elastic Beanstalk application, artifact bucket, IAM roles, and security groups. Each environment stack creates exactly one Elastic Beanstalk environment. Elastic Beanstalk owns the environment's target group and shared-listener host rule. Neither template creates an `AWS::ElasticBeanstalk::ApplicationVersion` or sets an environment `VersionLabel`; the deployment workflow creates application versions and updates environments after the stacks exist.

`dns.yaml` is optional and reserved for a future move of authoritative DNS to Route 53. Current cutover is manual in Cloudflare.

## Prerequisites and Defaults

Use `us-east-1` for this migration and the default VPC. DNS is currently authoritative in Cloudflare. Use an ACM wildcard or SAN certificate that covers all five slot hostnames and is issued in `us-east-1`, the ALB region.

The operator creating or updating infrastructure needs administrator-level credentials appropriate for CloudFormation, IAM, EC2, ACM, Elastic Beanstalk, and S3. This is separate from the restricted GitHub deployment role: routine workflow deployments do not create infrastructure stacks.

Discover the default VPC and public default subnets in different Availability Zones before creating the stacks:

```bash
aws ec2 describe-vpcs \
  --region us-east-1 \
  --filters Name=is-default,Values=true \
  --query 'Vpcs[0].VpcId' \
  --output text

aws ec2 describe-subnets \
  --region us-east-1 \
  --filters Name=vpc-id,Values=<DEFAULT_VPC_ID> Name=default-for-az,Values=true \
  --query 'Subnets[?MapPublicIpOnLaunch==`true`].[SubnetId,AvailabilityZone]' \
  --output table
```

Create or identify a GitHub Actions OIDC provider and record its ARN. Set the repository variable `AWS_FORGE_DEPLOY_ROLE_ARN` to the shared stack's `DeployRoleArn`. It is an OIDC role ARN, not an AWS access-key secret.

Do not store a GitHub OAuth app client ID or secret. The start.grails.org UI removed Push to GitHub, and the unused server-side create/OAuth integration is not part of this deployment. Keep `GITHUB_REDIRECT_URL` as the browser redirect to the Forge UI.

Grails 7 and Grails 8 both run on Java 25, so select a concrete Corretto 25 Elastic Beanstalk `PlatformArn`. Prefer an `arm64` platform with `t4g.small` only when that platform supports `arm64`; otherwise select an `x86_64` platform with `t3.small`.

```bash
aws elasticbeanstalk list-platform-versions \
  --region us-east-1 \
  --filters Type=PlatformName,Operator=contains,Values="Corretto 25" \
  --query 'PlatformSummaryList[].PlatformArn' \
  --output table

aws elasticbeanstalk describe-configuration-options \
  --region us-east-1 \
  --platform-arn <CORRETTO_25_PLATFORM_ARN> \
  --options Namespace=aws:ec2:instances,OptionName=SupportedArchitectures \
  --query 'Options[0].ValueOptions' \
  --output table
```

## Slots and Listener Rules

Create one environment stack for each row. The five listener priorities must remain unique.

| Slot | HostName | Environment name | ListenerRulePriority |
| --- | --- | --- | --- |
| `latest` | `latest.grails.org` | `grails-forge-latest` | 10 |
| `snapshot` | `snapshot.grails.org` | `grails-forge-snapshot` | 20 |
| `next` | `next.grails.org` | `grails-forge-next` | 30 |
| `prev` | `prev.grails.org` | `grails-forge-prev` | 40 |
| `prev-snapshot` | `prev-snapshot.grails.org` | `grails-forge-prev-snapshot` | 50 |

`HostName` supplies both the host-header condition and the application's `HOSTNAME` setting. Do not use a path rule or the listener default rule for a slot.

## Stack Deployment Order

Deploy `grails-forge/infrastructure/shared.yaml` first. Its exact required parameters are `VpcId`, `PublicSubnets`, `CertificateArn`, and `GitHubOidcProviderArn`; `ApplicationName` defaults to `grails-forge` and may be supplied explicitly. The workflow derives each environment name as `<ApplicationName>-<slot>`. Do not use `StackPrefix` or `PublicSubnetIds`.

```bash
aws cloudformation deploy \
  --region us-east-1 \
  --stack-name grails-forge-shared \
  --template-file grails-forge/infrastructure/shared.yaml \
  --capabilities CAPABILITY_NAMED_IAM \
  --parameter-overrides \
    VpcId=<DEFAULT_VPC_ID> \
    PublicSubnets='<SUBNET_ID_1>,<SUBNET_ID_2>' \
    CertificateArn=<ACM_CERTIFICATE_ARN> \
    GitHubOidcProviderArn=<GITHUB_OIDC_PROVIDER_ARN> \
    ApplicationName=grails-forge
```

Deploy five `environment.yaml` stacks next, using the slot values in the table. The command parameters are `SharedStackName`, `Slot`, `HostName`, `ListenerRulePriority`, `InstanceSubnets`, `PlatformArn`, `InstanceType`, and `Architecture`. `GitHubRedirectUrl` is an optional template parameter that defaults to `https://start.grails.org/`.

```bash
aws cloudformation deploy \
  --region us-east-1 \
  --stack-name grails-forge-latest \
  --template-file grails-forge/infrastructure/environment.yaml \
  --parameter-overrides \
    SharedStackName=grails-forge-shared \
    Slot=latest \
    HostName=latest.grails.org \
    ListenerRulePriority=10 \
    InstanceSubnets='<SUBNET_ID_1>,<SUBNET_ID_2>' \
    PlatformArn=<CORRETTO_25_PLATFORM_ARN> \
    InstanceType=<t4g.small_OR_t3.small> \
    Architecture=<arm64_OR_x86_64>
```

Repeat that command for the other four rows. Before the first Forge deployment, require only `Status=Ready` for each environment. With no `VersionLabel`, Elastic Beanstalk may run its Sample Application, which can be unhealthy because it does not provide `/versions`. Dispatch the first deployment workflow next; after it completes, require `Status=Ready` and `Health=Green`. Do not try to pass an application version or version label to CloudFormation.

## Artifact Packaging

From `grails-forge`, build the Elastic Beanstalk bundle with the repository task:

```bash
./gradlew grails-forge-web-netty:awsElasticBeanstalk
```

The output is `grails-forge-web-netty/build/distributions/grails-forge-web-netty-aws.zip`. Its ZIP root contains `app.jar`, `Procfile`, `start.sh`, and `.platform`. Do not create an `application.jar` archive manually. The workflow uploads this ZIP and creates a distinct immutable Elastic Beanstalk application version for the selected slot; it does not require the same artifact to be deployed to all five slots.

## GitHub Actions Deployment

GitHub registers `workflow_dispatch` from the default branch. Choose **Use workflow from** as the maintenance line to build, then choose the slot. Region, stack name, and JDK are not inputs. The deploy role trusts `refs/heads/*.x` and `refs/tags/v*` in `apache/grails-core`.

The workflow reads `AWS_FORGE_DEPLOY_ROLE_ARN` as a repository variable, assumes it with GitHub OIDC, derives `<ApplicationName>-<slot>`, packages the bundle, uploads it to the shared artifact bucket, creates the application version, and waits for that version to be `Processed` before updating the environment.

It then waits for `Ready` and `Green` and smoke-tests `/versions` through the shared ALB. Either failure triggers rollback when a prior real Forge application version exists. Automatic rollback is unavailable only when no prior real Forge version exists, including when the current environment version is the Elastic Beanstalk Sample Application.

Verify each environment after its workflow completes:

```bash
aws elasticbeanstalk describe-environments \
  --region us-east-1 \
  --environment-names <ENVIRONMENT_NAME> \
  --query 'Environments[0].[Status,Health,VersionLabel,CNAME]' \
  --output table
```

## Host-Header and SNI Verification

Get `SharedLoadBalancerDnsName` from the shared stack output. `--connect-to` reaches that ALB while preserving the slot hostname for TLS SNI and the host header.

```bash
export ALB_DNS_NAME=<SHARED_LOAD_BALANCER_DNS_NAME>
export SLOT_HOSTNAME=latest.grails.org

curl --fail --show-error --silent \
  --connect-to "${SLOT_HOSTNAME}:443:${ALB_DNS_NAME}:443" \
  --output /dev/null \
  "https://${SLOT_HOSTNAME}/versions"
```

Repeat for all five hostnames. Success proves certificate selection, SNI, the host rule, and target reachability. Public DNS already CNAME's these hostnames to the ALB, so the same check works without `--connect-to`.

## Cloudflare DNS

The five API hostnames already have DNS-only CNAME records targeting the shared ALB. Keep them unproxied. Do not create or change `start.grails.org`. To reverse traffic, restore the previous CNAME targets. Keep GCP available through the observation window so that reversal remains possible.

## Optional Future Route 53 DNS

Do not deploy `grails-forge/infrastructure/dns.yaml` while Cloudflare remains authoritative. After authoritative DNS moves to Route 53, it can create the five A alias records with only `SharedStackName` and `HostedZoneId`. The current template creates A aliases only; it does not create AAAA records. It deliberately does not modify `start.grails.org`.

## Routine Deployment and Rollback

1. Dispatch `.github/workflows/forge-deploy-aws.yml` for one slot.
2. Confirm the workflow used `AWS_FORGE_DEPLOY_ROLE_ARN`, completed with `Ready` and `Green`, and has the expected version label.
3. Smoke-test `https://<SLOT_HOSTNAME>/versions`.
4. Observe Elastic Beanstalk health, ALB target health, 5xx counts, response time, and CloudFormation events.

The workflow rolls back automatically if its deployment or `/versions` smoke test fails and a prior real Forge version exists. For an initial deployment whose prior version is the Elastic Beanstalk Sample Application, automatic rollback is unavailable and the operator must investigate the failed deployment. For a manual rollback, update the affected environment to its previous known-good Forge version label. Do not modify the shared listener rules or Cloudflare records for a single-slot application failure.

```bash
aws elasticbeanstalk update-environment \
  --region us-east-1 \
  --environment-name <ENVIRONMENT_NAME> \
  --version-label <PREVIOUS_GOOD_VERSION>
```

## Monitoring and Cost Defaults

The environment template uses enhanced health, `/versions` as the target-group health check, immutable deployments, a minimum of one instance, and a maximum of two instances. It streams Elastic Beanstalk logs to CloudWatch with 14-day retention. The application lifecycle removes unused Elastic Beanstalk versions after 90 days and deletes their source bundles; currently deployed versions remain protected. The shared artifact bucket separately expires noncurrent S3 object versions after 90 days.

Monitor Elastic Beanstalk health, ALB target health, ALB and target 5xx counts, response time, CloudWatch logs, and CloudFormation events. Keep the selected architecture and instance type aligned with the platform ARN. Review the one-to-two-instance capacity and artifact retention against actual traffic and cost after the observation window.

## Analytics Is Not Deployed

Analytics is intentionally and completely omitted from this migration. Do not deploy an analytics environment, database, endpoint, or analytics-related environment variable. Without those variables, reporting is disabled and Forge application generation continues normally.

Reintroduce analytics only as an independent project with its own infrastructure, persistence, retention, access controls, alarms, and rollout plan. Validate it independently before adding an endpoint or analytics environment variables to a non-production Forge slot.

## GCP Decommission

Do not decommission GCP immediately. Retain Cloud Run, Cloud SQL, credentials, and the prior configuration through the observation window while comparing availability, generation success, latency, error rates, and cost.

After the observation window closes and rollback is no longer required, remove traffic and credentials, verify that no client or scheduled job still depends on GCP, archive required configuration and logs, then decommission GCP through the approved change process.
