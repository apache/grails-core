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

An automatic GraphQL schema generator for [GORM](https://grails.apache.org/docs/latest/grails-data/).

This project is part of the main Grails monorepo build. The published modules
are wired into the root `settings.gradle`:

| Module          | Gradle path                  | Maven coordinates                                 |
| --------------- | ---------------------------- | ------------------------------------------------- |
| Core schema lib | `:grails-data-graphql-core`  | `org.apache.grails.data:grails-data-graphql-core` |
| Grails plugin   | `:grails-data-graphql`       | `org.apache.grails:grails-data-graphql`           |
| Reference guide | `:grails-data-graphql-docs`  | (not published)                                   |

## Building

Run from the repository root:

```bash
./gradlew :grails-data-graphql-core:build
./gradlew :grails-data-graphql:build
./gradlew :grails-data-graphql-docs:asciidoctor
```

## Example applications

Demo applications under `grails-test-examples/graphql/` will be added in a
follow-up PR (matching the layout used by `grails-test-examples/mongodb/` and
`grails-test-examples/hibernate5/`).

## Dependencies

- [graphql-java](https://github.com/graphql-java/graphql-java)
