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

# Development

## Useful Custom Gradle tasks

These tasks can be run like so:

`./gradlew publishGuide`

* `codeStyle` - runs all code style checks
* `publishGuide` - generates the user guide in the `grails-doc/build/original-guide`

## Various properties that control which tasks to run

These can be set on the command line like so:

`./gradlew check -PskipCodeStyle`

* `gebAtCheckWaiting` - enables Geb atCheckWaiting
* `onlyCoreTests` - runs tests that do not include mongo, hibernate, or functional
* `onlyFunctionalTests` - runs only grails-test-examples/* tests
* `onlyHibernate5Tests` - runs only a hibernate5 related test
* `onlyMongodbTests` - runs only a mongodb related test
* `onlySpringSecurityTests` - runs only spring security related tests
* `serializeMongoTests` - if true, only integration tests from one mongo project will run at a time
* `skipCodeStyle` - does not run code style checks
* `skipCoreTests` - does not run the "core" tests
* `skipFunctionalTests` - does not run the functional tests
* `skipHibernate5Tests` - does not run hibernate5 related tests
* `skipMongodbTests` - does not run mongo related tests
* `skipSpringSecurityTests` - does not run spring security related tests
* `skipTests` - no tests will run

## Test slicing

Test classes tagged with `@spock.lang.Tag('some-tag')` can be run separately.\
Tags are inherited from superclasses, so a test class is also included if any of its ancestors is tagged.

Example:

```bash
./gradlew iT -PincludeTestTags=geb
```

Available project properties:

* `includeTestTags` - comma-separated list of test tags to include
* `excludeTestTags` - comma-separated list of test tags to exclude

Example with multiple tags:

```bash
./gradlew iT -PincludeTestTags=geb,api
```

## Start a mongo docker container (containers will start by default)
`docker run -d  --name mongo-on-docker  -p 27017:27017 mongo`