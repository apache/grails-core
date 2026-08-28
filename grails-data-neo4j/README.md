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

* [Documentation](https://grails.apache.org/docs/latest/grails-data/neo4j/manual/)
* [API](https://grails.apache.org/docs/latest/api)

For the current development version see the following links:

* [Snapshot Documentation](https://grails.apache.org/docs/snapshot/grails-data/neo4j/manual/)
* [Snapshot API](https://grails.apache.org/docs/snapshot/api)

## Modules

This project is part of the main Grails monorepo build. The modules are wired into the root
`settings.gradle`:

| Module         | Gradle path                    | Maven coordinates                                |
| -------------- | ------------------------------- | ------------------------------------------------- |
| Core           | `:grails-data-neo4j-core`      | `org.apache.grails.data:grails-data-neo4j-core`    |
| Spring Boot    | `:grails-data-neo4j-spring-boot` | `org.apache.grails:grails-data-neo4j-spring-boot` |
| Grails plugin  | `:grails-data-neo4j`           | `org.apache.grails:grails-data-neo4j`              |
| Docs           | `:grails-data-neo4j-docs`      | (not published)                                    |

Example apps live under `grails-test-examples/neo4j/` (`base`, `hibernate5`, `spring-boot`,
`neo4j-standalone`, `test-data-service`), matching the layout used by the other datastores.
