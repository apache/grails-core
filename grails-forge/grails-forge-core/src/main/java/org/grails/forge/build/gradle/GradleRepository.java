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
package org.grails.forge.build.gradle;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.order.Ordered;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public interface GradleRepository extends Ordered {

    Pattern URL_SCHEME_PREFIX = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*://.*");

    @NonNull
    String toSnippet(String basePadding);

    static Set<GradleRepository> getDefaultRepositories(String grailsVersion) {
        return getDefaultRepositories(grailsVersion, System.getenv("GRAILS_REPO_URL"));
    }

    static Set<GradleRepository> getDefaultRepositories(String grailsVersion, String overrideRepo) {
        Set<GradleRepository> repositories = new LinkedHashSet<>();

        if (overrideRepo != null && !overrideRepo.isEmpty()) {
            List<String> overrides = Arrays.stream(overrideRepo.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

            for (String overrideUrl : overrides) {
                // The Gradle repository aliases render as their method calls rather than a
                // maven { url = ... } block, so the generated build resolves them natively
                if ("mavenLocal()".equals(overrideUrl)) {
                    repositories.add(new MavenLocalRepository(repositories.size()));
                } else if ("mavenCentral()".equals(overrideUrl)) {
                    repositories.add(new MavenCentralRepository(repositories.size()));
                } else {
                    repositories.add(
                        new DefaultGradleRepository(
                            repositories.size(),
                            validateOverrideRepository(overrideUrl)
                        )
                    );
                }
            }
        }
        if (repositories.stream().noneMatch(MavenCentralRepository.class::isInstance)) {
            repositories.add(new MavenCentralRepository(repositories.size()));
        }
        repositories.add(new DefaultGradleRepository(repositories.size(), "https://repo.grails.org/grails/restricted"));
        if (grailsVersion.endsWith("SNAPSHOT")) {
            repositories.add(new DefaultGradleRepository(
                repositories.size(),
                "https://repository.apache.org/content/groups/snapshots",
                null,
                List.of(
                    new VersionRegexRepoFilter(
                        "org[.]apache[.]grails.*", ".*", ".*-SNAPSHOT"
                    ),
                    new VersionRegexRepoFilter(
                        "org[.]apache[.]groovy.*", "groovy.*", ".*-SNAPSHOT"
                    )
                ),
                List.of(VersionType.SNAPSHOT)
            ));
            repositories.add(new DefaultGradleRepository(
                repositories.size(),
                "https://central.sonatype.com/repository/maven-snapshots",
                null,
                List.of(
                    new VersionRegexRepoFilter(
                        "org[.]sitemesh.*", ".*", ".*"
                    )
                ),
                List.of(VersionType.SNAPSHOT)
            ));
            repositories.add(new DefaultGradleRepository(
                repositories.size(),
                "https://repository.apache.org/content/groups/staging",
                null,
                List.of(
                    new VersionRegexRepoFilter(
                        "org[.]apache[.]grails[.]gradle", "grails-publish", ".*"
                    ),
                    new VersionRegexRepoFilter(
                        "org[.]apache[.]groovy.*", "groovy.*", ".*"
                    )
                ),
                List.of(VersionType.RELEASE)
            ));
        }

        return repositories;
    }

    private static String validateOverrideRepository(String overrideUrl) {
        // Local filesystem repositories (including Windows drive paths such as C:\repo,
        // C:/repo and drive-relative C:repo) are always allowed; only genuine remote URLs
        // are constrained to HTTPS.
        if (isLocalRepository(overrideUrl)) {
            return overrideUrl;
        }
        try {
            URI uri = new URI(overrideUrl);
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return overrideUrl;
            }
            throw new IllegalArgumentException("Remote GRAILS_REPO_URL repositories must use HTTPS: " + overrideUrl);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid GRAILS_REPO_URL repository: " + overrideUrl, e);
        }
    }

    // Keep this classifier identical in shape to the GRAILS_REPO_URL handling in the
    // wrapper's GrailsWrapperRepo so both tools agree on what is local vs remote.
    private static boolean isLocalRepository(String overrideUrl) {
        try {
            URI uri = new URI(overrideUrl);
            String scheme = uri.getScheme();
            // Local: no scheme (a plain path), an explicit file: scheme, or a single-letter
            // scheme with no authority — a Windows drive letter ("C:/repo", "C:repo"), not a
            // remote URL scheme ("c://host" carries an authority and stays remote).
            return scheme == null ||
                "file".equalsIgnoreCase(scheme) ||
                (scheme.length() == 1 && uri.getRawAuthority() == null);
        } catch (URISyntaxException e) {
            // Unparseable: a URL-shaped value (leading "scheme://") is a broken remote
            // override, classified remote so it is validated and rejected rather than
            // silently treated as a filesystem repository; anything else ("C:\repo",
            // paths with spaces) is a local path.
            return !URL_SCHEME_PREFIX.matcher(overrideUrl).matches();
        }
    }
}
