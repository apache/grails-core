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

# GORM for Neo4j

This project implements [GORM](https://grails.apache.org/docs/latest/grails-data/) for the Neo4j 3.x Graph Database using the Bolt Java Driver.

For more information see the following links:

* [Documentation](https://gorm.grails.org/latest/neo4j/manual)
* [API](https://gorm.grails.org/latest/neo4j/api)

For the current development version see the following links:

* [Snapshot Documentation](https://gorm.grails.org/snapshot/neo4j/manual)
* [Snapshot API](https://gorm.grails.org/snapshot/neo4j/api)

## Modules

This project is part of the main Grails monorepo build. The modules are wired into the root
`settings.gradle`:

| Module         | Gradle path                    | Maven coordinates                                |
| -------------- | ------------------------------- | ------------------------------------------------- |
| Core           | `:grails-datastore-gorm-neo4j` | `org.apache.grails.data:grails-datastore-gorm-neo4j` |
| Spring Boot    | `:gorm-neo4j-spring-boot`      | `org.apache.grails:gorm-neo4j-spring-boot`         |
| Grails plugin  | `:grails-data-neo4j`           | `org.apache.grails:grails-data-neo4j`              |

## Deferred: example applications and reference guide

The standalone example apps previously declared in this module's own `settings.gradle`
(`examples-grails3-neo4j`, `examples-grails3-neo4j-hibernate`, `examples-neo4j-standalone`,
`examples-neo4j-spring-boot`, `examples-test-data-service`) and the `docs` reference-guide module
are not yet part of the monorepo build. They are expected to be re-added in a follow-up, mirroring
how `grails-data-graphql`'s example apps were migrated into `grails-test-examples/graphql/`.
