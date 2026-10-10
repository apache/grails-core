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
The Grails project is structured into 3 separate composite builds. Composite builds make use of Gradle's `includeBuild` feature, which do not share Gradle plugins from `buildSrc`. This project exists to share internal Gradle plugins across all 3 separate builds.


The `plugins` project holds the shared convention plugins. The `vulnerability-scan` project holds the `org.apache.grails.buildsrc.vulnerability-scan` plugin, whose `vulnerabilityScan` task looks the dependencies of a project up in the OSV and Sonatype Guide vulnerability databases, and the `org.apache.grails.buildsrc.vulnerability-scan-report` plugin, which summarizes the scans of every project for the root build.

Both plugins cache the answers of the databases under `caches/grails-vulnerability-scan` in the Gradle user home, one small file per dependency and per vulnerability looked up. Nothing removes the entries: an entry older than the cache time to live (`vulnerabilityScan.cacheTtl`, an hour by default) is fetched again and overwritten, and the directory can be deleted at any time.
