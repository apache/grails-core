/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.buildsrc

import org.gradle.api.Plugin
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.initialization.Settings
import org.gradle.api.initialization.resolve.DependencyResolutionManagement
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.plugin.management.PluginManagementSpec

class GrailsRepoSettingsPlugin implements Plugin<Settings> {

    @Override
    void apply(Settings target) {
        target.pluginManagement { PluginManagementSpec manager ->
            manager.repositories { RepositoryHandler repo ->
                if (System.getenv('GRAILS_INCLUDE_MAVEN_LOCAL')) {
                    repo.mavenLocal()
                }
                repo.mavenCentral {
                    mavenContent {
                        it.releasesOnly()
                    }
                }
                repo.gradlePluginPortal()
                repo.maven {
                    url = 'https://repository.apache.org/content/groups/snapshots'
                    content {
                        it.includeVersionByRegex('org[.]apache[.]grails[.]gradle.*', '.*', '.*-SNAPSHOT')
                    }
                    mavenContent {
                        it.snapshotsOnly()
                    }
                }
                repo.maven {
                    url = 'https://central.sonatype.com/repository/maven-snapshots'
                    content {
                        it.includeVersionByRegex('cloud[.]wondrify.*', '.*', '.*-SNAPSHOT')
                    }
                    mavenContent {
                        it.snapshotsOnly()
                    }
                }
                repo.maven {
                    url = 'https://repository.apache.org/content/groups/staging'
                    content {
                        it.includeModuleByRegex('org[.]apache[.]grails[.]gradle', 'grails-publish')
                        it.includeModuleByRegex('org[.]apache[.]groovy', 'groovy.*')
                    }
                    mavenContent {
                        it.releasesOnly()
                    }
                }
            }
        }

        target.dependencyResolutionManagement { DependencyResolutionManagement manager ->
            manager.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
            manager.repositories { RepositoryHandler repo ->
                if (System.getenv('GRAILS_INCLUDE_MAVEN_LOCAL')) {
                    repo.mavenLocal()
                }
                // Maven Central first. Gradle disables a repository for the rest of the build on a transport
                // failure and fails resolution instead of trying the next repository, so the primary
                // repository has to be the one expected to stay up.
                repo.mavenCentral {
                    mavenContent {
                        it.releasesOnly()
                    }
                }
                // 8.14.5 of gradle-tooling-api is not on Maven Central. Ask only Gradle's release
                // repository for that module. A non-exclusive include leaves Central and mavenLocal
                // eligible if they have it; exclusiveContent would hide it from both.
                repo.maven {
                    url = 'https://repo.gradle.org/gradle/libs-releases'
                    content {
                        it.includeModule('org.gradle', 'gradle-tooling-api')
                    }
                    mavenContent {
                        it.releasesOnly()
                    }
                }
                // Plugin jars and plugin markers are published to the Plugin Portal, not Maven Central.
                // buildSrc consumes several of them as libraries. Central remains first, so a
                // release Central has is not also requested here.
                repo.gradlePluginPortal {
                    mavenContent {
                        it.releasesOnly()
                    }
                }
                repo.maven {
                    url = 'https://repository.apache.org/content/groups/snapshots'
                    content {
                        it.includeVersionByRegex('org[.]apache[.]grails.*', '.*', '.*-SNAPSHOT')
                        it.includeVersionByRegex('org[.]apache[.]groovy.*', '.*', '.*-SNAPSHOT')
                    }
                    mavenContent {
                        it.snapshotsOnly()
                    }
                }
                repo.maven {
                    url = 'https://central.sonatype.com/repository/maven-snapshots'
                    content {
                        it.includeVersionByRegex('cloud[.]wondrify.*', '.*', '.*-SNAPSHOT')
                    }
                    mavenContent {
                        it.snapshotsOnly()
                    }
                }
                repo.maven {
                    url = 'https://repository.apache.org/content/groups/staging'
                    content {
                        it.includeModuleByRegex('org[.]apache[.]grails[.]gradle', 'grails-publish')
                        it.includeModuleByRegex('org[.]apache[.]groovy[.]geb', 'geb.*')
                        it.includeModuleByRegex('org[.]apache[.]groovy', 'groovy.*')
                    }
                    mavenContent {
                        it.releasesOnly()
                    }
                }
            }
        }
    }
}
