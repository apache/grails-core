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
package org.grails.forge.build.gradle

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.grails.forge.feature.FeatureSelectionException
import org.springframework.core.Ordered
import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
interface GradleRepository extends Ordered {

    Pattern URL_SCHEME_PREFIX = Pattern.compile('[A-Za-z][A-Za-z0-9+.-]*://.*')

    Pattern KNOWN_SCHEME = Pattern.compile('(?i)(https|http|file|ftp|sftp|jar|s3|gs|ssh):')

    /**
     * What an entry of {@code GRAILS_REPO_URL} cannot have: it is written into the build file of the
     * application, where a line break, a quote or a {@code $} would be code of the build.
     */
    Pattern NOT_IN_A_REPOSITORY = Pattern.compile(/[\p{Cc}\p{Cf}\p{Zl}\p{Zp}'"$]/)

    @Nonnull
    String toSnippet(String basePadding)

    /**
     * The address of a repository as a message shows it. An address can hold a user and a password,
     * also where a parser does not see them as such, so it is never repeated as it was given: what is
     * shown is made of the parts of an address that parses as one with a host (the scheme, the host, the
     * port and the path, never a user, a query or a fragment), and of an address that does not parse so,
     * or that has an at sign anywhere after its host, only its scheme when that is one of a few, and its
     * length.
     *
     * @param address the address as it was given
     * @return what a message can say of it
     */
    static String shownAddress(String address) {
        if (address == null) {
            return 'an address that was not given'
        }
        try {
            URI uri = new URI(address)
            String path = uri.getRawPath() ?: ''
            // an at sign after the host is where a password with a slash, a question mark or a hash in it
            // ends: what was parsed as the host and its port is then the user and the start of the password
            String afterHost = (path + '?' + (uri.getRawQuery() ?: '') + '#' + (uri.getRawFragment() ?: '')).toLowerCase(Locale.ROOT)
            if (uri.getScheme() != null && !uri.isOpaque() && uri.getHost() != null && !afterHost.contains('@') && !afterHost.contains('%40')) {
                return "'" + FeatureSelectionException.shown("${uri.getScheme()}://${uri.getHost()}${uri.getPort() >= 0 ? ':' + uri.getPort() : ''}${path}".toString()) + "'"
            }
        } catch (URISyntaxException ignored) {
            // not shown then
        }
        Matcher scheme = KNOWN_SCHEME.matcher(address)
        return "an address that is not shown (${scheme.lookingAt() ? scheme.group(1).toLowerCase(Locale.ROOT) + ', ' : ''}${address.length()} characters)".toString()
    }

    static Set<GradleRepository> getDefaultRepositories(String grailsVersion) {
        return getDefaultRepositories(grailsVersion, System.getenv('GRAILS_REPO_URL'))
    }

    static Set<GradleRepository> getDefaultRepositories(String grailsVersion, String overrideRepo) {
        return defaultRepositories(grailsVersion, overrideRepo, false)
    }

    static Set<GradleRepository> getDefaultBuildRepositories(String grailsVersion) {
        return getDefaultBuildRepositories(grailsVersion, System.getenv('GRAILS_REPO_URL'))
    }

    static Set<GradleRepository> getDefaultBuildRepositories(String grailsVersion, String overrideRepo) {
        return defaultRepositories(grailsVersion, overrideRepo, true)
    }

    private static Set<GradleRepository> defaultRepositories(String grailsVersion, String overrideRepo, boolean buildRepositories) {
        Set<GradleRepository> repositories = new LinkedHashSet<>()

        if (overrideRepo != null && !overrideRepo.isEmpty()) {
            List<String> overrides = Arrays.stream(overrideRepo.split(';'))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList()

            for (int entry = 0; entry < overrides.size(); entry++) {
                String overrideUrl = overrides.get(entry)
                if (NOT_IN_A_REPOSITORY.matcher(overrideUrl).find()) {
                    // the entry is not repeated: it can have a password in it, and is not safe to show
                    throw new IllegalArgumentException("Entry ${entry + 1} of ${overrides.size()} of GRAILS_REPO_URL has a character the address or the path of a repository " +
                            'cannot have here: a control character, a quote or a $')
                }
                // The Gradle repository aliases render as their method calls rather than a
                // maven { url = ... } block, so the generated build resolves them natively
                if ('mavenLocal()'.equals(overrideUrl)) {
                    repositories.add(new MavenLocalRepository(repositories.size()))
                } else if ('mavenCentral()'.equals(overrideUrl)) {
                    repositories.add(new MavenCentralRepository(repositories.size()))
                } else {
                    repositories.add(
                        new DefaultGradleRepository(
                            repositories.size(),
                            validateOverrideRepository(overrideUrl)
                        )
                    )
                }
            }
        }
        if (repositories.stream().noneMatch { it instanceof MavenCentralRepository }) {
            repositories.add(new MavenCentralRepository(repositories.size()))
        }
        if (buildRepositories) {
            // The plugins of the plugins block are resolved again through the build script's repositories.
            // Without the Gradle Plugin Portal, the plugins that only it hosts come through repo.grails.org,
            // together with a request for the jar of each plugin marker, which is a POM-only module.
            repositories.add(new GradlePluginPortalRepository(repositories.size()))
        }
        // repo.grails.org hosts no snapshots, so a snapshot version is not looked for there, where every
        // request for one would miss before the snapshot repository below is asked.
        repositories.add(new DefaultGradleRepository(
            repositories.size(),
            'https://repo.grails.org/grails/restricted',
            null,
            Collections.emptyList(),
            List.of(VersionType.RELEASE)
        ))
        if (grailsVersion.endsWith('SNAPSHOT')) {
            repositories.add(new DefaultGradleRepository(
                repositories.size(),
                'https://repository.apache.org/content/groups/snapshots',
                null,
                List.of(
                    new VersionRegexRepoFilter(
                        'org[.]apache[.]grails.*', '.*', '.*-SNAPSHOT'
                    ),
                    new VersionRegexRepoFilter(
                        'org[.]apache[.]groovy.*', 'groovy.*', '.*-SNAPSHOT'
                    )
                ),
                List.of(VersionType.SNAPSHOT)
            ))
            repositories.add(new DefaultGradleRepository(
                repositories.size(),
                'https://central.sonatype.com/repository/maven-snapshots',
                null,
                List.of(
                    new VersionRegexRepoFilter(
                        'org[.]sitemesh.*', '.*', '.*'
                    )
                ),
                List.of(VersionType.SNAPSHOT)
            ))
            repositories.add(new DefaultGradleRepository(
                repositories.size(),
                'https://repository.apache.org/content/groups/staging',
                null,
                List.of(
                    new VersionRegexRepoFilter(
                        'org[.]apache[.]grails[.]gradle', 'grails-publish', '.*'
                    ),
                    new VersionRegexRepoFilter(
                        'org[.]apache[.]groovy.*', 'groovy.*', '.*'
                    )
                ),
                List.of(VersionType.RELEASE)
            ))
        }

        return repositories
    }

    private static String validateOverrideRepository(String overrideUrl) {
        // Local filesystem repositories (including Windows drive paths such as C:\repo,
        // C:/repo and drive-relative C:repo) are always allowed; only genuine remote URLs
        // are constrained to HTTPS.
        if (isLocalRepository(overrideUrl)) {
            return overrideUrl
        }
        try {
            URI uri = new URI(overrideUrl)
            if ('https'.equalsIgnoreCase(uri.getScheme())) {
                return overrideUrl
            }
            throw new IllegalArgumentException('Remote GRAILS_REPO_URL repositories must use HTTPS: ' + shownAddress(overrideUrl))
        } catch (URISyntaxException ignored) {
            // what could not be read as an address is said by the exception too, with a password that is in it
            throw new IllegalArgumentException('Invalid GRAILS_REPO_URL repository: ' + shownAddress(overrideUrl))
        }
    }

    // Keep this classifier identical in shape to the GRAILS_REPO_URL handling in the
    // wrapper's GrailsWrapperRepo so both tools agree on what is local vs remote.
    private static boolean isLocalRepository(String overrideUrl) {
        try {
            URI uri = new URI(overrideUrl)
            String scheme = uri.getScheme()
            // Local: no scheme (a plain path), an explicit file: scheme, or a single-letter
            // scheme with no authority — a Windows drive letter ("C:/repo", "C:repo"), not a
            // remote URL scheme ("c://host" carries an authority and stays remote).
            return scheme == null ||
                'file'.equalsIgnoreCase(scheme) ||
                (scheme.length() == 1 && uri.getRawAuthority() == null)
        } catch (URISyntaxException e) {
            // Unparseable: a URL-shaped value (leading "scheme://") is a broken remote
            // override, classified remote so it is validated and rejected rather than
            // silently treated as a filesystem repository; anything else ("C:\repo",
            // paths with spaces) is a local path.
            return !URL_SCHEME_PREFIX.matcher(overrideUrl).matches()
        }
    }
}
