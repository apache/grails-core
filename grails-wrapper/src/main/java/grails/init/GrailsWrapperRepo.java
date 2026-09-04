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
package grails.init;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Helper class to locate the remote or local repository for the `grails-cli`
 */
public class GrailsWrapperRepo {

    private static final Pattern URL_SCHEME_PREFIX = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*://.*");

    private String baseUrl;
    private String repoPath;
    private String metadataName;
    boolean isFile;

    private GrailsWrapperRepo() {
    }

    /**
     * @return the base url of the given repository
     */
    String getUrl() {
        return join(baseUrl, repoPath);
    }

    /**
     * Get the path to the root metadata file in the configured repository
     *
     * @return the url to the root metadata-maven.xml file
     */
    String getRootMetadataUrl() {
        return join(getUrl(), metadataName);
    }

    /**
     * Given a grails version, get the path to the version specific metadata file in the configured repository
     *
     * @param version the desired grails version
     * @return the url to the version specific metadata-maven.xml file
     */
    String getMetadataUrl(GrailsVersion version) {
        return join(getUrl(), version.version, metadataName);
    }

    /**
     * Given a grails version & a file name, get the path to that file in the configured repository
     *
     * @param version the grails version
     * @param name    the file name of the `grails-cli` jar
     * @return the file url to the `grails-cli` jar
     */
    String getFileUrl(GrailsVersion version, String name) {
        return join(getUrl(), version.version, name);

    }

    private String join(String... elements) {
        return String.join(isFile ? File.separator : "/", elements);
    }

    /**
     * @return the repo the wrapper should look for the `grails-cli` jar
     */
    static List<GrailsWrapperRepo> getSelectedRepos() {
        List<GrailsWrapperRepo> repos = new ArrayList<>();

        // Prefer the override repo first
        for (String overRepoUrl : getOverriddenMavenRepos()) {
            if (overRepoUrl != null) {
                System.out.println("...Update Repository is overridden to prefer [" + overRepoUrl + "].");
                repos.add(createGrailsWrapperRepo(overRepoUrl));
            }
        }

        // prefer maven central second
        repos.add(createGrailsWrapperRepo("https://repo1.maven.org/maven2"));

        // finally fallback to ASF repo (only valid for snapshot/stage testing)
        repos.add(createGrailsWrapperRepo("https://repository.apache.org/content/groups/public"));

        return repos;
    }

    static GrailsWrapperRepo createGrailsWrapperRepo(String urlOrFile) {
        String resolvedUrlOrFile = resolveRepositoryAlias(urlOrFile);
        GrailsWrapperRepo repo = new GrailsWrapperRepo();
        repo.isFile = isFileRepository(resolvedUrlOrFile);
        if (!repo.isFile) {
            validateRemoteRepositoryUrl(resolvedUrlOrFile);
        }
        repo.repoPath = repo.isFile ?
            String.join(File.separator, "org", "apache", "grails", GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME) :
            "org/apache/grails/" + GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME;
        repo.baseUrl = normalizeBaseUrl(resolvedUrlOrFile, repo.isFile);

        if ((repo.isFile && endsWithFileSeparator(repo.baseUrl)) || (!repo.isFile && repo.baseUrl.endsWith("/"))) {
            // remove trailing slash
            repo.baseUrl = repo.baseUrl.substring(0, repo.baseUrl.length() - 1);
        }

        repo.metadataName = repo.isFile ? "maven-metadata-local.xml" : "maven-metadata.xml";
        return repo;
    }

    /**
     * Resolves the Gradle-style repository aliases {@code mavenLocal()} and {@code mavenCentral()}
     * to the local Maven repository path and the Maven Central URL respectively. Keep the alias
     * handling identical in shape to the GRAILS_REPO_URL handling in grails-forge and the Grails
     * Shell CLI so all tools agree on what the aliases mean.
     */
    private static String resolveRepositoryAlias(String urlOrFile) {
        if ("mavenLocal()".equals(urlOrFile)) {
            return String.join(File.separator, System.getProperty("user.home"), ".m2", "repository");
        }
        if ("mavenCentral()".equals(urlOrFile)) {
            return "https://repo1.maven.org/maven2";
        }
        return urlOrFile;
    }

    private static void validateRemoteRepositoryUrl(String url) {
        try {
            URI uri = new URI(url);
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                throw new IllegalArgumentException("Grails wrapper remote repository URLs must use HTTPS: " + url);
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Grails wrapper remote repository URL: " + url, e);
        }
    }

    private static boolean isFileRepository(String urlOrFile) {
        try {
            URI uri = new URI(urlOrFile);
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
            // silently searched on the filesystem; anything else ("C:\repo", paths with
            // spaces) is a local path.
            return !URL_SCHEME_PREFIX.matcher(urlOrFile).matches();
        }
    }

    private static String normalizeBaseUrl(String urlOrFile, boolean fileRepository) {
        if (fileRepository) {
            try {
                URI uri = new URI(urlOrFile);
                if ("file".equalsIgnoreCase(uri.getScheme())) {
                    return new File(uri).getPath();
                }
            } catch (IllegalArgumentException | URISyntaxException ignored) {
                // not a file: URI — use the value as a plain path
            }
        }
        return urlOrFile;
    }

    private static boolean endsWithFileSeparator(String urlOrFile) {
        return urlOrFile.endsWith("/") || urlOrFile.endsWith(File.separator);
    }

    /**
     * @return the overridden maven repositories if configured via the property `grails.repo.url` or environment variable `GRAILS_REPO_URL`
     */
    static List<String> getOverriddenMavenRepos() {
        String baseUrl = System.getProperty("grails.repo.url");
        if (baseUrl != null) {
            return Arrays.stream(baseUrl.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        }

        String envBasedUrl = System.getenv("GRAILS_REPO_URL");
        if (envBasedUrl != null) {
            return Arrays.stream(envBasedUrl.split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        }

        return new ArrayList<>();
    }
}
