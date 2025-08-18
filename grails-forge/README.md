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

# Grails Application Forge

[![Maven Central](https://img.shields.io/maven-central/v/org.apache.grails.forge/grails-forge-core.svg?label=Maven%20Central)](https://search.maven.org/artifact/org.apache.grails.forge/grails-forge-core)

Generates Grails applications.

## Installation

The CLI application comes in various flavours from a universal Java applications to native applications for Windows, Linux and OS X. These are available for direct download on the [releases page](https://github.com/apache/grails-core/releases). For installation see the [Grails documentation](https://docs.grails.org/latest/guide/index.html#buildCLI).

If you prefer not to install an application to create Grails applications you can do so with `curl` directly from the API:

```bash
$ curl 'https://grailsforge-latest-cjmq3uyfcq-uc.a.run.app/demo.zip' -o demo.zip
$ unzip demo.zip -d demo
$ cd demo
$ ./gradlew run
```

Run `curl https://grailsforge-latest-cjmq3uyfcq-uc.a.run.app/` for more information on how to use the API or see the API documentation referenced below.

## UI

If you prefer a browser based user interface you can visit [Grails Forge](https://start.grails.org).

The user interface is [written in React](https://github.com/apache/grails-forge-ui/tree/main/app/launch) and is a static single page application that simply interacts with the https://start.grails.org API.

## API

API documentation for the production instance can be found at:

* [Swagger / OpenAPI Doc](https://grailsforge-latest-cjmq3uyfcq-uc.a.run.app/swagger-ui/index.html)
* [RAPI Doc](https://grailsforge-latest-cjmq3uyfcq-uc.a.run.app/rapidoc/index.html)

API documentation for the snapshot /development instance can be found at:

* [Swagger / OpenAPI Doc](https://grailsforge-snapshot-cjmq3uyfcq-uc.a.run.app/swagger-ui/index.html)
* [RAPI Doc](https://grailsforge-snapshot-cjmq3uyfcq-uc.a.run.app/rapidoc/index.html)

## Documentation

<!-- See the [Documentation](https://grails.github.io/grails-forge/6.0.x/guide/) for more information. -->

See the [Snapshot Documentation](https://grails.github.io/grails-forge/snapshot/guide/) for the current development docs.

## Snapshots and Releases

Releases are published to SDKMan via the Release action on [Github Actions](https://github.com/apache/grails-core/actions).

A release is performed with the following steps:

* [Publish the draft release](https://github.com/apache/grails-core/releases). There should be already a draft release created, edit and publish it. The Git Tag should start with `v`. For example `v1.0.0`.
* [Monitor the Workflow](https://github.com/apache/grails-core/actions?query=workflow%3ARelease) to check it passed successfully.
* Celebrate!

## Architecture

![Grails Forge Architecture](grailsforgearchitecture.jpeg)

## Distribution to Google Cloud Run via GitHub Actions


### Cloud Run

To make your life easier, export these environment variables so that you can copy and paste the commands used here. Choose whatever name you want, but the $PROJECT_ID has to be a unique name, because project IDs can't be reused in Google Cloud.

```
export PROJECT_ID=
export ACCOUNT_NAME=
```

For example, your commands should look something like this:

```
export PROJECT_ID=grailsforge
export ACCOUNT_NAME=grailsforge
```

Log in with your Google account:

```
gcloud auth login
```

```
gcloud projects create $PROJECT_ID
gcloud config set project $PROJECT_ID
```

Enable billing for your project, and create a billing profile if you don’t have one:

```
open "https://console.cloud.google.com/billing/linkedaccount?project=$PROJECT_ID"
```

Enable the necessary services:

```
gcloud services enable cloudbuild.googleapis.com run.googleapis.com containerregistry.googleapis.com
```

Create a service account:

```
gcloud iam service-accounts create $ACCOUNT_NAME \
--description="Cloud Run deploy account" \
--display-name="Cloud-Run-Deploy"
Give the service account Cloud Run Admin, Storage Admin, and Service Account User roles. You can’t set all of them at once, so you have to run separate commands:


gcloud projects add-iam-policy-binding $PROJECT_ID \
--member=serviceAccount:$ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com \
--role=roles/run.admin

gcloud projects add-iam-policy-binding $PROJECT_ID \
--member=serviceAccount:$ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com \
--role=roles/storage.admin

gcloud projects add-iam-policy-binding $PROJECT_ID \
--member=serviceAccount:$ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com \
--role=roles/iam.serviceAccountUser

gcloud projects add-iam-policy-binding $PROJECT_ID \
--member=serviceAccount:$ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com \
--role=roles/artifactregistry.writer
```

Generate a key.json file with your credentials, so your GitHub workflow can authenticate with Google Cloud:

```
gcloud iam service-accounts keys create key.json \
--iam-account $ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com
```

### GitHub Actions

GitHub, you need to set up a secrets environment in your repository, with the following values:

- `GCP_PROJECT_ID` is your `$PROJECT_ID`.
- `GCP_APP_NAME` is your app name.
- `GCP_EMAIL` is the email from the service account you created, which should look like this: `$ACCOUNT_NAME@$PROJECT_ID.iam.gserviceaccount.com`
- `GCP_CREDENTIALS` is the content from the key.json file that you just created.
- 
For example, your settings should look something like this:

`GCP_PROJECT_ID = grailsforge`  
`GCP_APP_NAME = grailsforge`  
`GCP_EMAIL = account-name@project-example.iam.gserviceaccount.com`    

Cat the `key.json` content and paste it into the `GCP_CREDENTIALS` secret value.

Now you just need to create a YAML file telling which commands your workflow should run. In your project directory, create a folder called `.github` and create another one inside it called `workflows`.

See the workflows in this project for examples.

## Cloud SQL Setup

![Cloud SQL Admin API Enable Screenshot](docs/enable-cloud-sql-admin-api.png)

### Cloud SQL Instance

Create an instance of Cloud SQL:

![](docs/cloud_sql_create_intance.png)

Choose PostgreSQL as your database engine: 

![](docs/choose-postgresql.png)

![Enable Compute Engine API](docs/enable-compute-engine-api.png)

Configure it with the following options: 

![](docs/dbinstance-options.png)

### Databases

Create two databases `grailsforge-production` and `grailsforge-snapshot`

![Cloud SQL Databases Screenshot](docs/create-two-databases.png)

## Cloud Run environment variables

### Latest Env

#### Cloud Run grailsforge-latest

| Name                                             | Value                                                                                                                                  | 
|:-------------------------------------------------|:---------------------------------------------------------------------------------------------------------------------------------------|
| `MICRONAUT_ENV_DEDUCTION`                        | `false`                                                                                                                                |
| `MICRONAUT_ENVIRONMENTS`                         | `gcp`                                                                                                                                  |
| `MICRONAUT_HTTP_SERVICES_ANALYTICS_READ_TIMEOUT` | `20s`                                                                                                                                  |
| `MICRONAUT_HTTP_SERVICES_ANALYTICS_URL`          | URL of cloud run `grailsforge-analytics-latest`                                                                                        |
| `GITHUB_OAUTH_APP_CLIENT_ID`                     | The Client ID shared after creating GitHub OAuth App                                                                                   |
| `GITHUB_OAUTH_APP_CLIENT_SECRET`                 | The Client secret shared after creating the GitHub OAuth app                                                                           |
| `GITHUB_USER_AGENT`                              | URL of the grails-forge-ui static website. For example, `https://start.grails.org/`. The default value is `https://start.grails.org/`. |
| `GITHUB_REDIRECT_URL`                            | URL of the grails-forge-ui static website. For example, `https://start.grails.org/`. The default value is `https://start.grails.org/`. |

### Cloud Run grailsforge-analytics-latest

| Name                           | Value                                            | 
|:-------------------------------|:-------------------------------------------------|
| `CLOUD_SQL_CONNECTION_NAME`    | `grailsforge:us-central1:grailsforge-postgresdb` |
| `DB_NAME`                      | `grailsforge-production`                         |
| `MICRONAUT_ENV_DEDUCTION`      | `false`                                          |
| `MICRONAUT_ENVIRONMENTS`       | `gcp`                                            |
| `DATASOURCES_DEFAULT_USERNAME` | `***`                                            |
| `DATASOURCES_DEFAULT_PASSWORD` | `***`                                            |
| `DATASOURCES_DEFAULT_URL`      | `jdbc:postgresql:///grailsforge-production`      |


### Snapshot Env

### Cloud Run grailsforge-snapshot

| Name                                             | Value                                                                                                                                                  | 
|:-------------------------------------------------|:-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `MICRONAUT_ENV_DEDUCTION`                        | `false`                                                                                                                                                |
| `MICRONAUT_ENVIRONMENTS`                         | `gcp`                                                                                                                                                  |
| `MICRONAUT_HTTP_SERVICES_ANALYTICS_READ_TIMEOUT` | `20s`                                                                                                                                                  |
| `MICRONAUT_HTTP_SERVICES_ANALYTICS_URL`          | URL of cloud run `grailsforge-analytics-snapshot`                                                                                                      |
| `GITHUB_OAUTH_APP_CLIENT_ID`                     | The Client ID shared after creating GitHub OAuth App                                                                                                   |
| `GITHUB_OAUTH_APP_CLIENT_SECRET`                 | The Client secret shared after creating the GitHub OAuth app                                                                                           |
| `GITHUB_USER_AGENT`                              | URL of the grails-forge-ui static website. For example, `https://start.grails.org/`. The default value is `https://grails.github.io/grails-forge-ui/`. |
| `GITHUB_REDIRECT_URL`                            | URL of the grails-forge-ui static website. For example, `https://start.grails.org/`. The default value is `https://grails.github.io/grails-forge-ui/`. |


### Cloud Run grailsforge-analytics-latest

| Name                           | Value                                            | 
|:-------------------------------|:-------------------------------------------------|
| `CLOUD_SQL_CONNECTION_NAME`    | `grailsforge:us-central1:grailsforge-postgresdb` |
| `DB_NAME`                      | `grailsforge-snapshot`                           |
| `MICRONAUT_ENV_DEDUCTION`      | `false`                                          |
| `MICRONAUT_ENVIRONMENTS`       | `gcp`                                            |
| `DATASOURCES_DEFAULT_USERNAME` | `***`                                            |
| `DATASOURCES_DEFAULT_PASSWORD` | `***`                                            |
| `DATASOURCES_DEFAULT_URL`      | `jdbc:postgresql:///grailsforge-snapshot`        |


