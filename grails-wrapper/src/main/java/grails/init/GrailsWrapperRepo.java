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
package grails.init;

import java.io.File;

/**
 * Helper class to locate the remote or local repository for the `grails-cli`
 */
public final class GrailsWrapperRepo {

    boolean isFile;
    private String baseUrl;
    private String repoPath;
    private String metadataName;

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
    static GrailsWrapperRepo getSelectedRepo() {
        GrailsWrapperRepo repo = new GrailsWrapperRepo();
        repo.repoPath = "org/apache/grails/" + GrailsWrapperHome.CLI_COMBINED_PROJECT_NAME;

        String configured = getConfiguredMavenUrl();
        if (configured != null) {
            System.out.println("...Update Repository is overridden to: " + configured);
            repo.baseUrl = configured;
        } else {
            // default to upstream snapshots or groups
            repo.baseUrl = "https://repository.apache.org/content/groups/public";
        }
        repo.isFile = !repo.baseUrl.startsWith("http");

        if ((repo.isFile && repo.baseUrl.endsWith(File.separator)) || (!repo.isFile && repo.baseUrl.endsWith("/"))) {
            // remove trailing slash
            repo.baseUrl = repo.baseUrl.substring(0, repo.baseUrl.length() - 1);
        }

        repo.metadataName = repo.isFile ? "maven-metadata-local.xml" : "maven-metadata.xml";
        return repo;
    }

    /**
     * @return the override maven repository if configured via the property `grails.repo.url` or environment variable `GRAILS_REPO_URL`
     */
    static String getConfiguredMavenUrl() {
        String baseUrl = System.getProperty("grails.repo.url");
        if (baseUrl != null) {
            return baseUrl;
        }

        return System.getenv("GRAILS_REPO_URL");
    }
}
