/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.cli.profile.repository

import grails.util.Environment
import org.eclipse.aether.artifact.Artifact
import spock.lang.Specification

/**
 * Regression tests for {@link AbstractJarProfileRepository#getProfileArtifact}.
 *
 * <p>These tests guard against the version-resolution failure observed when the Grails
 * CLI is invoked with {@code GRAILS_REPO_URL} pointing at an Apache staging repository
 * whose published BOM does not declare {@code org.apache.grails.profiles:*} coordinates.
 * Prior to the fix, {@code getProfileArtifact} returned an {@link Artifact} with a
 * {@code null} version, the BOM-managed version fallback in
 * {@code MavenResolverGrapeEngine.createArtifact} also returned {@code null}, and Aether
 * surfaced a "Could not find artifact org.apache.grails.profiles:web:jar:" error
 * (with an empty version) against {@code grails-override-repo}.
 */
class AbstractJarProfileRepositorySpec extends Specification {

    private static AbstractJarProfileRepository newRepository() {
        new StaticJarProfileRepository(Thread.currentThread().contextClassLoader, new URL[0])
    }

    void "getProfileArtifact defaults to the running Grails version when no settings are configured"() {
        given: 'a profile repository with no grails.profiles settings'
        def repo = newRepository()

        when: 'a bare profile name is resolved'
        Artifact art = repo.getProfileArtifact('web')

        then: 'the artifact has the default group, the requested artifact id, and the running Grails version'
        art.groupId == 'org.apache.grails.profiles'
        art.artifactId == 'web'
        art.version == Environment.grailsVersion
        art.version != null
        !art.version.isEmpty()
    }

    void "getProfileArtifact preserves an explicit GAV passed by the caller"() {
        given:
        def repo = newRepository()

        when:
        Artifact art = repo.getProfileArtifact('com.acme.profiles:custom-web:9.9.9')

        then:
        art.groupId == 'com.acme.profiles'
        art.artifactId == 'custom-web'
        art.version == '9.9.9'
    }
}
