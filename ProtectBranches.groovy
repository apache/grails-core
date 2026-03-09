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
import groovy.json.JsonBuilder

def githubToken = System.getenv("GITHUB_TOKEN") ?: System.getProperty("github.token")
if (!githubToken) {
    throw new IllegalStateException("GitHub token not configured. Set the GITHUB_TOKEN environment variable or the 'github.token' system property.")
}
def repoOwner   = "apache"
def repoName    = "grails-core"
def baseApiUrl  = "https://api.github.com/repos/${repoOwner}/${repoName}"

def tableData = """
| origin/1.1.x | 2009-11-26 | RELEASE |
| origin/1.3.0.RC2 | 2010-04-23 | RELEASE |
| origin/1.2.x | 2010-10-11 | RELEASE |
| origin/1.3.x | 2012-06-01 | RELEASE |
| origin/2.0.x | 2013-05-30 | RELEASE |
| origin/2.1.x | 2013-09-21 | RELEASE |
| origin/2.2.x | 2014-07-27 | RELEASE |
| origin/2.3.x | 2015-06-17 | RELEASE |
| origin/2.4.x | 2015-09-08 | RELEASE |
| origin/3.0.x | 2016-07-27 | RELEASE |
| origin/3.1.x | 2017-05-09 | RELEASE |
| origin/3.1.x-issue-9058 | 2017-05-23 | RELEASE |
| origin/3.2.x | 2019-10-10 | RELEASE |
| origin/master | 2021-11-24 | RELEASE |
| origin/4.0.x | 2022-06-03 | RELEASE |
| origin/5.1.x | 2022-10-13 | RELEASE |
| origin/5.0.x | 2022-11-25 | RELEASE |
| origin/5.2.x | 2023-02-13 | RELEASE |
| origin/2.5.x | 2023-12-17 | RELEASE |
| origin/3.3.x | 2024-01-09 | RELEASE |
| origin/4.1.x | 2024-01-26 | RELEASE |
| origin/5.3.x | 2024-01-26 | RELEASE |
| origin/6.1.x | 2024-02-27 | RELEASE |
| origin/6.0.x | 2024-04-09 | RELEASE |
| origin/5.4.x | 2024-09-11 | RELEASE |
| origin/6.2.x | 2025-01-03 | RELEASE |
| origin/gh-pages | 2025-01-07 | RELEASE |
| origin/7.1.x | 2026-02-27 | RELEASE |
| origin/8.0.x | 2026-02-28 | RELEASE |
| origin/7.0.x | 2026-03-04 | RELEASE |
| origin/main | 2026-03-04 | RELEASE |
"""

tableData.eachLine { line ->
    if (!line.contains("|") || line.contains("---") || line.contains("Branch")) return null

    def parts = line.split("\\|").collect { it.trim() }
    if (parts.size() < 4) return null

    def branch = parts[1].replace("origin/", "")
    def type   = parts[3]

    if (type == "RELEASE") {
        protectBranch(baseApiUrl, githubToken, branch)
    } else {
        println "SKIPPING [${type}]: ${branch}"
    }
    return null
}

/**
 * Sets the branch to 'Read Only' and prevents deletion
 * unless the 'enforce_admins' rule is manually toggled.
 */
def protectBranch(baseUrl, token, branch) {
    println "PROTECTING: ${branch}"
    def url = new URL("${baseUrl}/branches/${branch}/protection")
    def body = new JsonBuilder([
            enforce_admins               : true,
            required_status_checks       : [
                    strict  : true,
                    contexts: []
            ],
            required_pull_request_reviews: [
                    required_approving_review_count: 1,
                    dismiss_stale_reviews          : true,
                    require_code_owner_reviews     : false
            ],
            restrictions                 : null,
            allow_force_pushes           : false,
            allow_deletions              : false
    ]).toString()

    sendRequest(url, token, "PUT", body)
}

def sendRequest(url, token, method, body) {
    try {
        def conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("Authorization", "token ${token}")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        if (body) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.withWriter { it << body }
        }

        if (conn.responseCode in [200, 201, 204]) {
            println "SUCCESS: ${conn.responseCode}"
        } else {
            println "FAILED: ${conn.responseCode} - ${conn.errorStream?.text}"
        }
    } catch (Exception e) {
        println "ERROR: ${e.message}"
    }
}
