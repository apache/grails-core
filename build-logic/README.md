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
# build-logic
The Grails project is structured into 2 separate composite builds (`grails-core` and `grails-gradle`). Composite builds make use of Gradle's `includeBuild` feature, which do not share Gradle plugins from `buildSrc`. This project exists to share internal Gradle plugins across those builds. Forge lives under `grails-forge/` as `grails-core` subprojects, so it uses these plugins through the root build.


The `plugins` project holds the shared convention plugins. The `vulnerability-scan` project holds only the `org.apache.grails.buildsrc.vulnerability-scan` plugin, because the Sonatype scan plugin it wraps is a fat jar that bundles Guava, Groovy and other libraries without relocating them; keeping it in its own project keeps those bundled classes off the settings classpath that every build shares when it requests a plugin from `plugins`.
