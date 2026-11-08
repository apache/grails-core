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
package org.grails.cli.profile.repository

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.xml.XmlSlurper

import org.eclipse.aether.artifact.Artifact
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency

import grails.util.Environment
import org.grails.cli.boot.GrailsDependencyVersions
import org.grails.cli.compiler.grape.DependencyResolutionContext
import org.grails.cli.compiler.grape.DependencyResolutionFailedException
import org.grails.cli.compiler.grape.MavenResolverGrapeEngine
import org.grails.cli.profile.Profile

/**
 *  Resolves profiles from a configured list of repositories using Aether
 *
 * @author Graeme Rocher
 * @since 3.1
 */
@CompileStatic
class MavenProfileRepository extends AbstractJarProfileRepository {

    static final URI GRAILS_REPO_URI = new URI('https://repo.grails.org/grails/restricted')
    static final URI APACHE_REPO_URI = new URI('https://repository.apache.org/content/groups/public')

    /**
     * Whether the given Grails version should resolve against snapshot repositories: only a snapshot
     * build (or an unknown version) does. Kept as a pure function of the version so the decision can
     * be made during the CLI lifecycle — and unit tested — rather than at class-initialisation time.
     */
    static boolean isSnapshotVersion(String grailsVersion) {
        !grailsVersion || grailsVersion.endsWith('SNAPSHOT')
    }

    /**
     * Whether snapshot resolution should be enabled for the running CLI. Reading the version here
     * (rather than in a static initialiser) keeps the environment access on the startup lifecycle.
     * For a milestone/release CLI this is {@code false}, which lets Aether use
     * {@code UPDATE_POLICY_NEVER} and serve fixed versions straight from the local cache instead of
     * making remote {@code maven-metadata.xml} round-trips on every resolve.
     */
    static boolean snapshotsEnabled() {
        isSnapshotVersion(Environment.grailsVersion)
    }

    static GrailsRepositoryConfiguration grailsRepo() {
        new GrailsRepositoryConfiguration('grailsRepo', GRAILS_REPO_URI, snapshotsEnabled())
    }

    static GrailsRepositoryConfiguration apacheRepo() {
        new GrailsRepositoryConfiguration('apacheRepository', APACHE_REPO_URI, snapshotsEnabled())
    }

    List<GrailsRepositoryConfiguration> repositoryConfigurations
    MavenResolverGrapeEngine grapeEngine
    GroovyClassLoader classLoader
    DependencyResolutionContext resolutionContext
    GrailsDependencyVersions profileDependencyVersions
    private boolean resolved = false

    MavenProfileRepository(List<GrailsRepositoryConfiguration> repositoryConfigurations) {
        this.repositoryConfigurations = repositoryConfigurations
        classLoader = new GroovyClassLoader(Thread.currentThread().contextClassLoader)
        resolutionContext = new DependencyResolutionContext()
        this.grapeEngine = GrailsMavenResolverGrapeEngineFactory.create(classLoader, repositoryConfigurations, resolutionContext)
        profileDependencyVersions = new GrailsDependencyVersions(grapeEngine)
        resolutionContext.addDependencyManagement(profileDependencyVersions)
    }

    MavenProfileRepository() {
        // Use apache repository with SNAPSHOTS when grailsVersion is not set or it ends in SNAPSHOT
        // otherwise use only mavenCentral
        this(snapshotsEnabled() ? [apacheRepo(), grailsRepo()] : [grailsRepo()])
    }

    @Override
    Profile getProfile(String profileName, Boolean parentProfile) {
        String profileShortName = profileName
        if (profileName.contains(':')) {
            def art = new DefaultArtifact(profileName)
            profileShortName = art.artifactId
        }
        if (!profilesByName.containsKey(profileShortName)) {
            if (parentProfile && profileDependencyVersions.find(DEFAULT_PROFILE_GROUPID, profileShortName)) {
                return resolveProfile(profileShortName)
            } else {
                return resolveProfile(profileName)
            }
        }
        return super.getProfile(profileShortName)
    }

    @Override
    Profile getProfile(String profileName) {
        getProfile(profileName, false)
    }

    protected Profile resolveProfile(String profileName) {
        Artifact art = getProfileArtifact(profileName)

        try {
            grapeEngine.grab(group: art.groupId, module: art.artifactId, version: art.version ?: null)
        } catch (DependencyResolutionFailedException e) {

            def localData = new File(System.getProperty('user.home'), "/.m2/repository/${art.groupId.replace('.', '/')}/$art.artifactId/maven-metadata-local.xml")
            if (localData.exists()) {
                def currentVersion = parseCurrentVersion(localData)
                def profileFile = new File(localData.parentFile, "$currentVersion/${art.artifactId}-${currentVersion}.jar")
                if (profileFile.exists()) {
                    classLoader.addURL(profileFile.toURI().toURL())
                }
                else {
                    throw e
                }
            }
            else {
                throw e
            }
        }

        processUrls()
        return super.getProfile(art.artifactId)
    }

    @CompileDynamic
    protected String parseCurrentVersion(File localData) {
        new XmlSlurper().parse(localData).versioning.versions.version[0].text()
    }

    protected void processUrls() {
        def urls = classLoader.getURLs()
        for (URL url in urls) {
            registerProfile(url, new URLClassLoader([url] as URL[], Thread.currentThread().contextClassLoader))
        }
    }

    @Override
    List<Profile> getAllProfiles() {
        if (!resolved) {
            List<Map> profiles = []
            resolutionContext.managedDependencies.each { Dependency dep ->
                if (dep.artifact.groupId == 'org.apache.grails.profiles') {
                    profiles.add([group: dep.artifact.groupId, module: dep.artifact.artifactId])
                }
            }
            profiles.sort { it.module }

            for (Map profile in profiles) {
                grapeEngine.grab(profile)
            }

            def localData = new File(System.getProperty('user.home'), '/.m2/repository/org/apache/grails/profiles')
            if (localData.exists()) {
                localData.eachDir { File dir ->
                    if (!dir.name.startsWith('.')) {
                        def profileData = new File(dir, '/maven-metadata-local.xml')
                        if (profileData.exists()) {
                            def currentVersion = parseCurrentVersion(profileData)
                            def profileFile = new File(dir, "$currentVersion/${dir.name}-${currentVersion}.jar")
                            if (profileFile.exists()) {
                                classLoader.addURL(profileFile.toURI().toURL())
                            }
                        }
                    }
                }
            }

            processUrls()
            resolved = true
        }
        return super.getAllProfiles()
    }
}
