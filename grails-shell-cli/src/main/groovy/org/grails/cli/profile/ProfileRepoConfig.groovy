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

import grails.util.BuildSettings
import grails.util.Environment
import org.grails.cli.GrailsCli

import java.nio.file.Paths

class ProfileRepoConfig {
    String name
    String url
    boolean snapshots

    String username
    String password

    static List<ProfileRepoConfig> getConfiguredRepositories() {
        List<ProfileRepoConfig> repos = []

        // If there is manual configuration, honor it
        Map profileRepos = GrailsCli.getSetting(BuildSettings.PROFILE_REPOSITORIES, Map.class, Collections.emptyMap())
        if (profileRepos) {
            for (repoName in profileRepos.keySet()) {
                def data = profileRepos.get(repoName)
                if (data instanceof Map) {
                    String uri = data.get("url") as String
                    def snapshots = data.get('snapshotsEnabled')
                    if (uri != null) {
                        boolean enableSnapshots = snapshots != null ? Boolean.valueOf(snapshots.toString()) : false
                        final String username = data.get('username') as String
                        final String password = data.get('password') as String
                        repos << new ProfileRepoConfig(name: repoName as String, url: uri, snapshots: enableSnapshots, username: username as String, password: password as String)
                    }
                }
            }
        }

        // If the repo url from the wrapper is set, then the wrapper has been configured for a local install, so honor it as a valid source
        String repoUrl = System.getProperty("grails.repo.url") ?: System.getenv('GRAILS_REPO_URL')
        if (repoUrl) {
            repos << new ProfileRepoConfig(name: "grails-override-repo", url: fixRepoUrl(repoUrl), snapshots: Environment.grailsVersion.endsWith("SNAPSHOT"))
        }

        repos
    }

    private static String fixRepoUrl(String repoUrl) {
        try {
            URI uri = new URI(repoUrl)
            if (uri.getScheme() != null) {
                return repoUrl
            }
        } catch (URISyntaxException e) {
            // Not a valid URI, fall through to file conversion
        }

        if (repoUrl.startsWith('http')) {
            return repoUrl
        }

        Paths.get(repoUrl).toUri().toString()
    }
}
