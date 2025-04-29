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

# Grails Core Release Process

1. Perform the release of any other dependent library and update the version in `gradle.properties`/`grails-bom/plugins.properties`
1. Ensure you have the latest changes locally `git pull`
1. Ensure all changes from previous branches are merged up `git merge ...`
1. Ensure there are no snapshot dependencies 
1. Ensure the latest build passed
1. Run the pre-release workflow with the desired version # & branch; give a description to reflect you are starting the release process
1. For each project in the following list, draft a new release (tag should start with v, when generating release notes, be sure the release version doesn't have a v prefix), wait for release to publish, then update the grails-bom in grails core, & repeat the pre-release workflow in grails-core
    * grails-gsp
    * grails-data-mapping
    * gorm-hibernate5
    * gorm-mongodb
    * gorm-neo4j
    * fields
    * scaffolding
    * grails-views
    * geb
    * grails-cache
    * grails-profiles
1. Verify the release worked 
 * Run `sdk install grails XXX` and perform smoke tests or creating an application etc.
 * Check the documentation published to docs.grails.org/XXX
1. Run the maven central sync `./gradlew sWMC`. Requires BINTRAY_USER/BINTRAY_KEY env vars or bintrayUser/bintrayKey gradle properties
1. Ensure grails.org shows the new release version
1. Ensure the documentation published correctly docs.grails.org
1. Create a release in Github. Copy the previous release and change the relevant info
1. Change the version in `build.gradle` back to a snapshot of next release
1. Push the code `git push` 
1. Announce the Release
