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
package org.grails.cli.profile

import java.util.regex.Pattern

import groovy.transform.CompileStatic

/**
 * Shared handling of the {@code grails.repo.url} system property / {@code GRAILS_REPO_URL}
 * environment variable repository overrides, applying the same classification and HTTPS
 * validation as the Grails wrapper ({@code GrailsWrapperRepo}) and Grails Forge
 * ({@code GradleRepository}): local filesystem repositories are always allowed, remote
 * repositories must use HTTPS, and malformed remote values are rejected.
 */
@CompileStatic
final class GrailsRepositoryOverrides {

    private static final Pattern URL_SCHEME_PREFIX = Pattern.compile('[A-Za-z][A-Za-z0-9+.-]*://.*')

    private GrailsRepositoryOverrides() {
    }

    /**
     * @return the configured repository overrides (possibly empty), each entry validated
     */
    static List<String> getConfiguredOverrides() {
        String overrideRepo = System.getProperty('grails.repo.url') ?: System.getenv('GRAILS_REPO_URL')
        if (!overrideRepo) {
            return Collections.emptyList()
        }
        overrideRepo.split(';')
                .collect { String override -> override.trim() }
                .findAll { String override -> !override.isEmpty() }
                .collect { String override -> validateOverrideRepository(override) }
    }

    /**
     * Validates a single override: local filesystem repositories (including Windows drive
     * paths) pass through, remote repositories must use HTTPS, anything else is rejected.
     *
     * @param overrideUrl the configured override
     * @return the override, when valid
     */
    static String validateOverrideRepository(String overrideUrl) {
        if (isRepositoryAlias(overrideUrl) || isLocalRepository(overrideUrl)) {
            return overrideUrl
        }
        try {
            URI uri = new URI(overrideUrl)
            if ('https'.equalsIgnoreCase(uri.scheme)) {
                return overrideUrl
            }
            throw new IllegalArgumentException("Remote GRAILS_REPO_URL repositories must use HTTPS: ${overrideUrl}")
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid GRAILS_REPO_URL repository: ${overrideUrl}", e)
        }
    }

    /**
     * @param overrideUrl the configured override
     * @return true when the override is one of the Gradle repository aliases
     * {@code mavenLocal()} or {@code mavenCentral()}
     */
    static boolean isRepositoryAlias(String overrideUrl) {
        overrideUrl == 'mavenLocal()' || overrideUrl == 'mavenCentral()'
    }

    /**
     * Resolves the Gradle repository aliases to the concrete location they stand for — the
     * local Maven repository path and the Maven Central URL — for consumers that resolve
     * artifacts directly rather than writing the alias into a Gradle build.
     *
     * @param overrideUrl the configured override
     * @return the resolved location, or the override unchanged when it is not an alias
     */
    static String resolveRepositoryAlias(String overrideUrl) {
        if (overrideUrl == 'mavenLocal()') {
            return [System.getProperty('user.home'), '.m2', 'repository'].join(File.separator)
        }
        if (overrideUrl == 'mavenCentral()') {
            return 'https://repo1.maven.org/maven2'
        }
        overrideUrl
    }

    /**
     * Keep this classifier identical in shape to the GRAILS_REPO_URL handling in the
     * wrapper's {@code GrailsWrapperRepo} and forge's {@code GradleRepository} so all
     * tools agree on what is local vs remote.
     *
     * @param overrideUrl the configured override
     * @return true when the override refers to a local filesystem repository
     */
    static boolean isLocalRepository(String overrideUrl) {
        try {
            URI uri = new URI(overrideUrl)
            String scheme = uri.scheme
            // Local: no scheme (a plain path), an explicit file: scheme, or a single-letter
            // scheme with no authority — a Windows drive letter ("C:/repo", "C:repo"), not a
            // remote URL scheme ("c://host" carries an authority and stays remote).
            return scheme == null ||
                    'file'.equalsIgnoreCase(scheme) ||
                    (scheme.length() == 1 && uri.rawAuthority == null)
        } catch (URISyntaxException ignored) {
            // Unparseable: a URL-shaped value (leading "scheme://") is a broken remote
            // override, classified remote so it is validated and rejected rather than
            // silently treated as a filesystem repository; anything else ("C:\repo",
            // paths with spaces) is a local path.
            return !URL_SCHEME_PREFIX.matcher(overrideUrl).matches()
        }
    }

}
