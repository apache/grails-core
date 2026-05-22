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

# GORM for GraphQL

This project generates a GraphQL schema based on entities mapped with [GORM](https://grails.apache.org/docs/latest/grails-data/).

For more information see the following links:

* [Documentation](https://grails.apache.org/docs/latest/grails-data/graphql/manual/)
* [API](https://grails.apache.org/docs/latest/api)

For the current development version see the following links:

* [Snapshot Documentation](https://grails.apache.org/docs/snapshot/grails-data/graphql/manual/)
* [Snapshot API](https://grails.apache.org/docs/snapshot/api)

## Modules

The plugin is split across the following modules in the root `settings.gradle`:

| Module          | Gradle path                  | Maven coordinates                                 |
| --------------- | ---------------------------- | ------------------------------------------------- |
| Core schema lib | `:grails-data-graphql-core`  | `org.apache.grails.data:grails-data-graphql-core` |
| Grails plugin   | `:grails-data-graphql`       | `org.apache.grails:grails-data-graphql`           |
| Reference guide | `:grails-data-graphql-docs`  | (not published)                                   |

## Example applications

Five demo applications live under `grails-test-examples/graphql/`:

| Project gradle path                                            | Description                                              |
| -------------------------------------------------------------- | -------------------------------------------------------- |
| `:grails-test-examples-graphql-grails-test-app`                | End-to-end Grails REST app exercising every type/feature |
| `:grails-test-examples-graphql-grails-docs-app`                | Grails REST app backing the reference-guide examples     |
| `:grails-test-examples-graphql-grails-tenant-app`              | Grails app demonstrating GORM multi-tenancy              |
| `:grails-test-examples-graphql-grails-multi-datastore-app`     | Grails app combining Hibernate5 + MongoDB datastores     |
| `:grails-test-examples-graphql-spring-boot-app`                | Standalone Spring Boot app embedding the schema generator core |
