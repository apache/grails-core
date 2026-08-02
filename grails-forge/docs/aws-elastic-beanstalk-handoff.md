<!-- SPDX-License-Identifier: Apache-2.0 -->

# WIP Handoff: Grails Forge AWS Elastic Beanstalk Migration

> This is a temporary branch handoff. Keep it while the migration is paused and remove or fold it into the permanent runbook before opening a pull request.

## Restart Point

- Local branch: `feat/forge-aws-beanstalk`
- Base branch: `origin/7.0.x`
- Base commit: `8f2f6cf1e6`
- Remote status: intentionally not pushed
- Pull request: intentionally not created
- Detailed operator runbook: `grails-forge/docs/aws-elastic-beanstalk.md`
- Existing build issue discovered during validation: [apache/grails-core#16046](https://github.com/apache/grails-core/issues/16046)

When work resumes, check out this branch and review its local commits:

```shell
git checkout feat/forge-aws-beanstalk
git fetch origin
git log --oneline origin/7.0.x..HEAD
```

## Goal

Replace the five Grails Forge API deployments on Google Cloud Run with five ordinary JVM applications on AWS Elastic Beanstalk. The environments share one Application Load Balancer and use host-header routing for the existing slot hostnames.

The migration deliberately avoids native images, Docker deployment, the unused analytics service, and its database.

## Confirmed Decisions

| Decision | Selected approach |
| --- | --- |
| AWS region | `us-east-1` |
| Network | ASF account default VPC and public default subnets |
| Runtime | Elastic Beanstalk Corretto 17 Java SE platform |
| Preferred instances | ARM64 `t4g.small` when the selected platform supports ARM64 |
| Fallback instances | x86_64 `t3.small` with the matching platform |
| API slots | `latest`, `snapshot`, `next`, `prev`, `prev-snapshot` |
| Hostnames | Existing `*.grails.org` slot hostnames |
| UI | Leave `start.grails.org` where it is |
| AWS authentication | GitHub Actions OIDC, not long-lived AWS keys |
| Current DNS | Manual Cloudflare DNS-only CNAME cutover |
| Future DNS | Optional Route 53 template after authoritative DNS moves |
| Analytics | Completely omitted, with no service, RDS, endpoint, or environment variables |

## Implemented Architecture

The shared CloudFormation stack owns:

- One public Application Load Balancer with HTTP-to-HTTPS redirect and an ACM certificate.
- One S3 artifact bucket.
- The Elastic Beanstalk application.
- Elastic Beanstalk service and instance roles.
- The branch-scoped GitHub OIDC deployment role.
- ALB and instance security groups.

Each of five environment stacks owns one Elastic Beanstalk environment. Elastic Beanstalk creates the environment target group and host-header listener rule on the shared HTTPS listener.

The deployment workflow builds a normal executable JAR source bundle, waits for the Elastic Beanstalk application version to finish processing, updates one selected environment, waits for `Ready` and `Green` on the expected version label, smoke-tests `/versions` directly through the new ALB, and rolls back to the previous real Forge version when possible.

## Important Files

| Path | Purpose |
| --- | --- |
| `grails-forge/infrastructure/shared.yaml` | Shared ALB, S3, IAM, Elastic Beanstalk application, and security groups |
| `grails-forge/infrastructure/environment.yaml` | Parameterized five-slot Elastic Beanstalk environment stack |
| `grails-forge/infrastructure/dns.yaml` | Optional future Route 53 aliases only |
| `grails-forge/infrastructure/README.md` | Concise CloudFormation deployment commands |
| `grails-forge/grails-forge-web-netty/build.gradle` | `awsElasticBeanstalk` ZIP task |
| `grails-forge/grails-forge-web-netty/aws/` | `Procfile`, launcher, and nginx settings |
| `.github/workflows/forge-deploy-aws.yml` | Manual slot-aware OIDC deployment workflow |
| `grails-forge/docs/aws-elastic-beanstalk.md` | Full bootstrap, deploy, rollback, DNS, and decommission runbook |
| `grails-forge/README.md` | Public deployment overview while retaining the current live API URLs until cutover |
| `grails-forge/grails-forge-api/src/main/java/org/grails/forge/api/GrailsForgeConfiguration.java` | Corrected configured redirect binding |
| `grails-forge/grails-forge-api/src/test/groovy/org/grails/forge/api/ApplicationControllerSpec.groovy` | Redirect binding regression coverage |

## Verification Already Completed

- All three CloudFormation templates pass `cfn-lint` for `us-east-1`.
- Changed YAML and Java files have clean language-server diagnostics.
- Forge API and web tests pass through the targeted verification path.
- The redirect configuration binding regression test passes.
- `grails-forge-web-netty:awsElasticBeanstalk` produces the expected ZIP.
- The ZIP root contains `app.jar`, `Procfile`, `start.sh`, and `.platform`.
- The extracted JAR starts with the Micronaut `aws` environment.
- `/versions` returns HTTP 200 with the expected Grails version metadata.
- A browser-shaped request to `/` returns HTTP 308 to `https://start.grails.org/`.
- An unknown route returns HTTP 404.
- Final architecture and implementation review returned GREEN.

The forced composite-build `--rerun-tasks` failure is tracked separately as issue #16046. The same targeted Forge validation succeeds without forcing every included-build task.

## Account Values Needed Later

Collect these before deploying anything:

- The default VPC ID in `us-east-1`.
- At least two public default-subnet IDs in different Availability Zones.
- An ACM certificate ARN in `us-east-1` covering all five API hostnames.
- The AWS account GitHub OIDC provider ARN.
- The GitHub OAuth application client ID and the ARN of its secret stored in Secrets Manager with the default AWS managed KMS key.
- Confirmation that the chosen Corretto 17 Elastic Beanstalk platform and EC2 instance architecture match.

Do not place these values in this file or commit them to the repository.

## Resume Checklist

1. Run `git fetch origin`, then rebase or merge the latest `origin/7.0.x` into the local feature branch if necessary.
2. Re-run the targeted tests, package task, `cfn-lint`, and extracted-JAR smoke test after resolving any drift.
3. Deploy `grails-forge/infrastructure/shared.yaml` with administrator credentials.
4. Deploy `environment.yaml` once for each slot using unique listener priorities.
5. Add the shared stack output `DeployRoleArn` as repository variable `AWS_FORGE_DEPLOY_ROLE_ARN`.
6. Add `.github/workflows/forge-deploy-aws.yml` at the same path on the repository's default branch so GitHub registers its manual dispatch event.
7. Dispatch the workflow once per slot with `7.0.x` selected as the run ref.
8. Require every real Forge deployment to reach `Ready` and `Green` on the expected version label and pass the ALB-pinned `/versions` smoke test.
9. Create five DNS-only Cloudflare CNAME records targeting the shared ALB DNS name.
10. Leave `start.grails.org` unchanged.
11. Observe AWS and retain GCP for a rollback window before decommissioning Cloud Run.

## Do Not Do Yet

- Do not push this branch until work resumes and the local commits are reviewed again against current `7.0.x`.
- Do not create a pull request from this temporary checkpoint.
- Do not change Cloudflare DNS before all five AWS environments pass pre-cutover checks.
- Do not decommission Cloud Run at DNS cutover.
- Do not add analytics or a database unless a separate future requirement justifies them.
