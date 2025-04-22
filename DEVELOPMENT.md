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

# Various properties that control which & how tests run
* onlyFunctionalTests - runs only grails-test-examples/* tests
* onlyHibernate5Tests - runs only a hibernate5 related test
* onlyMongodbTests - runs only a mongodb related test
* onlyCoreTests - runs tests that do not include mongo, hibernate, or functional
* skipFunctionalTests - does not run the functional tests
* skipHibernate5Tests - does not run hibernate5 related tests
* skipMongodbTests - does not run mongo related tests
* skipCoreTests - does not run the "core" tests
* serializeMongoTests - if true, only integration tests from one mongo project will run at a time
* skipTests - no tests will run

# Start a mongo docker container (containers will start by default)
`docker run -d  --name mongo-on-docker  -p 27017:27017 mongo`