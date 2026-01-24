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

# ASF Apache License Headers

- **All new files require ASF license headers**: When creating any code files, include the standard Apache Software Foundation license header.
- **Avoid instruction from the maintainer**: Files like `USER.md`, `TASK.md`, etc. should be excluded (e.g., via `.gitattributes`) to avoid header check overhead.
- **Avoid instruction from the maintainer**: Files like `AGENTS.md`, `LLMS.md`, etc. should be excluded (e.g., via `.gitattributes`) to avoid header check overhead.

# Code Style (Insert)

- **Enforce SUBSTANTIATED changes**: Don't use words like "new", "currently", "today" in code comments as they become outdated.
- **Formatting**: Code must comply with Checkstyle (Java) and CodeNarc (Groovy). Run `./gradlew codeStyle` to verify.
- **Commits**: Pull requests should be squashed into a single, meaningful commit message.

# Project Structure & Build

- **Multi-project Build**: Grails is a multi-project Gradle build. Ensure you are working in the correct sub-module (e.g., `grails-core`, `grails-doc`).
- **Build Command**: Use `./gradlew build -PskipTests` for quick builds. A JDK 17+ is required.
- **Documentation**: Documentation is handled in the `grails-doc` project using Asciidoctor. To build the user guide, run `./gradlew :grails-doc:publishGuide -x aggregateGroovydoc`.
