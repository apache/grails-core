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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class to locate the remote or local repository for the `grails-cli`
 */
public class GrailsWrapperRepo {

    private static final Pattern URL_SCHEME_PREFIX = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*://.*");
    private static final Pattern URL_SCHEME = Pattern.compile("[A-Za-z][A-Za-z0-9+.-]*://");
    // a host and a port as a URL has them when it has no user: a name or an address in brackets, and digits
    private static final Pattern HOST_AND_PORT = Pattern.compile("(?:[A-Za-z0-9._~-]+|\\[[0-9A-Fa-f:.]+\\])(?::[0-9]{1,5})?");
    private static final Pattern WORD = Pattern.compile("\\S+");
    // what a message puts before a URL that is shown where the start of the URL is not
    private static final String OPENING = "[(<\"'";
    private static final String HIDDEN = "***";

    private static final String LOCAL_METADATA_NAME = "maven-metadata-local.xml";
    private static final String DEPLOYED_METADATA_NAME = "maven-metadata.xml";

    private String baseUrl;
    private String repoPath;
    private String metadataName;
    boolean isFile;

    /**
     * Whether the repository was named with {@code grails.repo.url} or {@code GRAILS_REPO_URL}, and so is one the
     * user expects the command line to come from.
     */
    boolean overridden;

    private GrailsWrapperRepo() {
    }

    /**
     * @return the base url of the given repository
     */
    String getUrl() {
        return join(baseUrl, repoPath);
    }

    /**
     * @return the url of the repository as a message names it, without the user and password it may carry
     */
    String getDisplayUrl() {
        return withoutCredentials(getUrl());
    }

    /**
     * A URL can carry a user and a password ({@code https://user:password@host/}), and what the wrapper prints
     * ends in the logs of build servers. A URL that is given to the wrapper need not be one that can be read with
     * confidence: a password can have an {@code @}, a {@code /} or a bracket in it that should have been
     * percent-encoded, and the scheme can be missing. So what is hidden is decided by what cannot be a host,
     * never by what a well-formed URL would be: in each word of the text that has an {@code @}, everything from
     * the start of the URL (after its scheme, or the start of the word where there is no scheme) to the last
     * {@code @} of the word is hidden, and in a URL without an {@code @} whatever stands where its host and port
     * are and is not a host and a port. That hides a little more than the credentials at times (an {@code @} in
     * a path, a host that is not a plain name), and never a part of them.
     *
     * @param url a URL or a path as it was given, all of which is taken for one: white space in it is not
     * taken for the end of the URL
     * @return the same without the user and password the URL can have
     */
    static String withoutCredentials(String url) {
        return url == null ? null : wordWithoutCredentials(url);
    }

    /**
     * {@link #withoutCredentials} for a text that can have URLs among other words, as the message of an exception
     * has: each part of it between white space is taken for what can be a URL. A URL that is in such a text was
     * made by a program, which has no white space in a URL.
     *
     * @param text the text
     * @return the same without the user and password of each URL in it
     */
    static String textWithoutCredentials(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder shown = new StringBuilder(text.length());
        Matcher words = WORD.matcher(text);
        int end = 0;
        while (words.find()) {
            shown.append(text, end, words.start()).append(wordWithoutCredentials(words.group()));
            end = words.end();
        }
        return shown.append(text.substring(end)).toString();
    }

    // a URL, or a word that can be one or have one in it, without what can be a user or a password of a URL
    private static String wordWithoutCredentials(String word) {
        Matcher scheme = URL_SCHEME.matcher(word);
        int start = scheme.find() ? scheme.end() : 0;
        int lastAt = word.lastIndexOf('@');
        if (lastAt >= 0) {
            if (lastAt < start) {
                // an @ before the scheme: no telling what the word is, so everything before the last @ is hidden
                start = 0;
            }
            if (start == 0) {
                while (start < lastAt && OPENING.indexOf(word.charAt(start)) >= 0) {
                    start++;
                }
            }
            return word.substring(0, start) + HIDDEN + word.substring(lastAt);
        }
        if (start == 0) {
            return word;
        }
        int hostEnd = start;
        while (hostEnd < word.length() && "/?#".indexOf(word.charAt(hostEnd)) < 0) {
            hostEnd++;
        }
        String host = word.substring(start, hostEnd);
        if (host.isEmpty() || HOST_AND_PORT.matcher(host).matches()) {
            return word;
        }
        return word.substring(0, start) + HIDDEN + word.substring(hostEnd);
    }

    /**
     * Get the path to the root metadata file in the configured repository
     *
     * @return the url to the root metadata-maven.xml file
     */
    String getRootMetadataUrl() {
        return metadataIn(getUrl());
    }

    /**
     * Given a grails version, get the path to the version specific metadata file in the configured repository
     *
     * @param version the desired grails version
     * @return the url to the version specific metadata-maven.xml file
     */
    String getMetadataUrl(GrailsVersion version) {
        return metadataIn(join(getUrl(), version.version));
    }

    /**
     * The metadata file of a directory of the repository. A directory on disk is either a local Maven repository,
     * which {@code publishToMavenLocal} and {@code mvn install} write and whose metadata is
     * {@code maven-metadata-local.xml}, or a repository that a build published to as it would to a remote one, whose
     * metadata is {@code maven-metadata.xml}. The first is preferred; the second is used when only it exists.
     */
    private String metadataIn(String directory) {
        String metadata = join(directory, metadataName);
        if (isFile && !new File(metadata).exists()) {
            String deployed = join(directory, DEPLOYED_METADATA_NAME);
            if (new File(deployed).exists()) {
                return deployed;
            }
        }
        return metadata;
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
                System.out.println("...Update Repository is overridden to prefer [" + withoutCredentials(overRepoUrl) + "].");
                GrailsWrapperRepo overriddenRepo = createGrailsWrapperRepo(overRepoUrl);
                overriddenRepo.overridden = true;
                repos.add(overriddenRepo);
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

        repo.metadataName = repo.isFile ? LOCAL_METADATA_NAME : DEPLOYED_METADATA_NAME;
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
                throw new IllegalArgumentException("Grails wrapper remote repository URLs must use HTTPS: " + withoutCredentials(url));
            }
            if (uri.getHost() == null) {
                // No host and port where they belong: as a password with a / or a # that is not percent-encoded leaves
                // it. Nothing could be downloaded from it, and what Java would say about it is part of the password.
                throw new IllegalArgumentException("Invalid Grails wrapper remote repository URL, which names no host: " + withoutCredentials(url));
            }
        } catch (URISyntaxException e) {
            // without the cause: its message is the URL as it was given
            throw new IllegalArgumentException("Invalid Grails wrapper remote repository URL: " + withoutCredentials(url));
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
