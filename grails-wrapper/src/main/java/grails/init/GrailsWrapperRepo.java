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

public class GrailsWrapperRepo {
    private String baseUrl;
    private String repoPath;
    private String metadataName;
    boolean isFile;

    private GrailsWrapperRepo() {
    }

    String getUrl() {
        return join(baseUrl, repoPath);
    }

    String getRootMetadataUrl() {
        return join(getUrl(), metadataName);
    }

    String getMetadataUrl(GrailsVersion version) {
        return join(getUrl(), version.version, metadataName);
    }

    String getFileUrl(GrailsVersion version, String name) {
        return join(getUrl(), version.version, name);

    }

    private String join(String ... elements) {
        return String.join(isFile ? File.separator : "/", elements);
    }

    static GrailsWrapperRepo getSelectedRepo() {
        GrailsWrapperRepo repo = new GrailsWrapperRepo();
        repo.repoPath = "org/apache/grails/" + GrailsHome.CLI_COMBINED_PROJECT_NAME;

        String configured = getConfiguredMavenUrl();
        if (configured != null) {
            System.out.println("... Update Repository is overridden to: " + configured);
            repo.baseUrl = configured;
        } else {
            // default to upstream snapshots or groups
            repo.baseUrl = "https://repository.apache.org/content/groups/public";
        }
        repo.isFile = !repo.baseUrl.startsWith("http");

        if((repo.isFile && repo.baseUrl.endsWith(File.separator)) || (!repo.isFile && repo.baseUrl.endsWith("/"))) {
            // remove trailing slash
            repo.baseUrl = repo.baseUrl.substring(0, repo.baseUrl.length() - 1);
        }

        repo.metadataName = repo.isFile ? "maven-metadata-local.xml" : "maven-metadata.xml";
        return repo;
    }

    static String getConfiguredMavenUrl() {
        String baseUrl = System.getProperty("grails.repo.url");
        if (baseUrl != null) {
            return baseUrl;
        }

        return System.getenv("GRAILS_REPO_URL");
    }
}
