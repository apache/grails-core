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
                // The Spring Dependency Management example imports grails-bom through
                // io.spring.dependency-management, which resolves BOM imports with its own detached
                // configuration and so never sees the project substitution in
                // gradle/functional-test-config.gradle. The root build script writes the BOM poms this
                // build generates into .gradle/local-boms, and they are served from here. That location
                // is deliberately outside build/, so a combined `gradlew clean <task>` invocation cannot
                // delete the poms after configuration has written them but before the example resolves.
                //
                // This is declared as exclusiveContent deliberately: these coordinates must resolve
                // from the local build and nowhere else. A plain content filter would let a missing
                // local pom fall through to the remote repositories and silently manage the example
                // with the last published BOM instead of the one in this commit - which is the exact
                // bug this arrangement exists to prevent, and it fails silently.
                //
                // Scoped to the grails-core root build, which is the only build that produces these
                // poms, so other consumers of this settings plugin are unaffected.
                if (new File(target.rootDir, 'grails-test-examples/spring-dependency-management').isDirectory()) {
                    repo.exclusiveContent {
                        it.forRepository {
                            repo.maven {
                                name = 'grailsLocalTestRepo'
                                url = new File(target.rootDir, '.gradle/local-boms').toURI()
                                metadataSources { source -> source.mavenPom() }
                            }
                        }
                        it.filter { filter ->
                            filter.includeModule('org.apache.grails', 'grails-base-bom')
                            filter.includeModule('org.apache.grails', 'grails-bom')
                            filter.includeModule('org.apache.grails', 'grails-hibernate5-bom')
                            filter.includeModule('org.apache.grails', 'grails-hibernate7-bom')
                        }
                    }
                }
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
