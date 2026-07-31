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
                repo.mavenCentral()
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
                        it.includeVersionByRegex('org[.]sitemesh.*', '.*', '.*-SNAPSHOT')
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
                repo.maven {
                    url = 'https://repo.grails.org/grails/restricted'
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
                        it.includeVersionByRegex('org[.]sitemesh.*', '.*', '.*-SNAPSHOT')
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
