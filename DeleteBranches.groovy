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
def githubToken = System.getenv('GITHUB_TOKEN')
def repoOwner   = "apache"
def repoName    = "grails-core"
def baseApiUrl  = "https://api.github.com/repos/${repoOwner}/${repoName}"

if (!githubToken || githubToken == "YOUR_PERSONAL_ACCESS_TOKEN") {
    throw new IllegalStateException(
        "GitHub token is required. Set the GITHUB_TOKEN environment variable."
    )
}
def tableData = """
| origin/GRAILS-6737-Groovy-1.7.5 | 2010-09-17 | CLOSED |
| origin/GRAILS-6278 | 2010-09-17 | CLOSED |
| origin/GRAILS-5087 | 2012-12-07 | CLOSED |
| origin/GRAILS-9997 | 2013-07-30 | CLOSED |
| origin/GRAILS-10533 | 2013-10-14 | CLOSED |
| origin/GRAILS-10512 | 2013-10-14 | CLOSED |
| origin/GRAILS-10613 | 2013-10-15 | CLOSED |
| origin/GRAILS-10660 | 2013-10-29 | CLOSED |
| origin/GRAILS-10631 | 2013-11-05 | CLOSED |
| origin/GRAILS-10728 | 2013-11-06 | CLOSED |
| origin/GRAILS-10448 | 2013-11-06 | CLOSED |
| origin/GRAILS-10780 | 2013-11-19 | CLOSED |
| origin/GRAILS-10813 | 2013-11-21 | CLOSED |
| origin/GRAILS-10838 | 2013-11-25 | CLOSED |
| origin/GRAILS-10826 | 2013-11-26 | CLOSED |
| origin/GRAILS-10835 | 2013-11-27 | CLOSED |
| origin/GRAILS-10853 | 2013-12-02 | CLOSED |
| origin/GRAILS-10868 | 2013-12-03 | CLOSED |
| origin/GRAILS-10871 | 2013-12-03 | CLOSED |
| origin/GRAILS-9664 | 2013-12-04 | CLOSED |
| origin/GRAILS-10882 | 2013-12-09 | CLOSED |
| origin/GRAILS-10852 | 2013-12-13 | CLOSED |
| origin/GRAILS-10910 | 2013-12-13 | CLOSED |
| origin/GRAILS-10908 | 2013-12-14 | CLOSED |
| origin/GRAILS-10683b | 2013-12-23 | CLOSED |
| origin/GRAILS-10897 | 2014-01-08 | CLOSED |
| origin/GRAILS-8426 | 2014-01-10 | CLOSED |
| origin/GRAILS-10973 | 2014-01-13 | CLOSED |
| origin/GRAILS-11003 | 2014-01-22 | CLOSED |
| origin/GRAILS-11011 | 2014-01-22 | CLOSED |
| origin/GRAILS-11075 | 2014-02-04 | CLOSED |
| origin/GRAILS-11093 | 2014-02-06 | CLOSED |
| origin/GRAILS-11104 | 2014-02-11 | CLOSED |
| origin/GRAILS-10683 | 2014-02-13 | CLOSED |
| origin/GRAILS-11145 | 2014-02-26 | CLOSED |
| origin/GRAILS-11197 | 2014-03-10 | CLOSED |
| origin/GRAILS-9686 | 2014-03-14 | CLOSED |
| origin/GRAILS-10031 | 2014-03-17 | CLOSED |
| origin/GRAILS-11222 | 2014-03-18 | CLOSED |
| origin/GRAILS-11238 | 2014-03-19 | CLOSED |
| origin/GRAILS-11242 | 2014-03-24 | CLOSED |
| origin/GRAILS-11204 | 2014-05-01 | CLOSED |
| origin/GRAILS-6766 | 2014-05-01 | CLOSED |
| origin/GRAILS-9996 | 2014-05-08 | CLOSED |
| origin/GRAILS-10905 | 2014-05-08 | CLOSED |
| origin/GRAILS-11448 | 2014-05-27 | CLOSED |
| origin/GRAILS-11453 | 2014-05-28 | CLOSED |
| origin/GRAILS-11462 | 2014-06-02 | CLOSED |
| origin/GRAILS-11129 | 2014-06-10 | CLOSED |
| origin/GRAILS-11505 | 2014-06-13 | CLOSED |
| origin/GRAILS-11505B | 2014-06-17 | CLOSED |
| origin/GRAILS-11505C | 2014-06-17 | CLOSED |
| origin/GRAILS-11585 | 2014-07-16 | CLOSED |
| origin/GRAILS-11576 | 2014-07-25 | CLOSED |
| origin/GRAILS-11625 | 2014-08-04 | CLOSED |
| origin/GRAILS-11543 | 2014-08-14 | CLOSED |
| origin/GRAILS-11666 | 2014-08-18 | CLOSED |
| origin/GRAILS-11661 | 2014-08-18 | CLOSED |
| origin/GRAILS-11686 | 2014-08-26 | CLOSED |
| origin/GRAILS-11680 | 2014-10-21 | CLOSED |
| origin/GRAILS-11791 | 2014-10-23 | CLOSED |
| origin/GRAILS-11748 | 2014-10-23 | CLOSED |
| origin/GRAILS-11806 | 2014-10-30 | CLOSED |
| origin/GRAILS-11638 | 2014-10-30 | CLOSED |
| origin/GRAILS-11976 | 2015-02-09 | CLOSED |
| origin/GRAILS-11973 | 2015-02-09 | CLOSED |
| origin/GRAILS-11958 | 2015-02-09 | CLOSED |
| origin/GRAILS-12112 | 2015-03-25 | CLOSED |
| origin/issue_9183 | 2015-08-13 | CLOSED |
| origin/issue10188 | 2016-10-27 | CLOSED |
| origin/issue10282 | 2016-11-19 | CLOSED |
| origin/GRAILS-10300-10315 | 2016-12-09 | CLOSED |
| origin/issue10423 | 2017-01-24 | CLOSED |
| origin/GRAILS-10392 | 2017-02-09 | CLOSED |
| origin/issue10502 | 2017-02-27 | CLOSED |
| origin/issue10600 | 2017-04-22 | CLOSED |
| origin/issue-10844 | 2018-02-26 | CLOSED |
| origin/issue-10844_take2 | 2020-11-05 | CLOSED |
| origin/feature/scaffolding-5.1.0 | 2024-09-11 | CLOSED |
| origin/renovate/major-jansi.version | 2024-10-24 | CLOSED |
| origin/renovate/major-javahamcrest-monorepo | 2024-10-24 | CLOSED |
| origin/add-grails-events-transform | 2024-12-23 | CLOSED |
| origin/renovate/alpine-3.x | 2024-12-27 | CLOSED |
| origin/renovate/actions-upload-artifact-4.x | 2025-02-21 | CLOSED |
| origin/renovate/com.gradle.develocity-3.x | 2025-02-25 | CLOSED |
| origin/renovate/io.micronaut.serde-micronaut-serde-jackson-2.x | 2025-03-03 | CLOSED |
| origin/renovate/io.micronaut-micronaut-http-client-4.x | 2025-03-13 | CLOSED |
| origin/issue-14804 | 2025-06-11 | CLOSED |
| origin/retry-build-step | 2025-06-18 | CLOSED |
| origin/renovate/micronautversion | 2025-07-12 | CLOSED |
| origin/addition-micronaut-feature-test | 2025-08-06 | CLOSED |
| origin/update-rest-transform-dependency | 2025-08-07 | CLOSED |
| origin/JavaExec-argsFile | 2025-09-22 | CLOSED |
| origin/java-25-support-test | 2025-09-24 | CLOSED |
| origin/fix-starter-published-dependencies | 2025-10-04 | CLOSED |
| origin/fix/null-constructor-arg-groovy4 | 2026-03-03 | CLOSED |
| origin/issue_8974 | 2015-03-23 | MERGE |
| origin/issue_610 | 2015-04-06 | MERGE |
| origin/issue-11211 | 2019-01-03 | MERGE |
| origin/patch-decouple-gradle | 2024-07-24 | MERGE |
| origin/web-profile-jar-artifact | 2025-10-08 | MERGE |
| origin/jrebelFeatureFix | 2025-10-29 | MERGE |
| origin/chore/gsp_and_gson_dependencies_and_apply | 2025-11-12 | MERGE |
| origin/fix/issue_15228-respond-errors | 2025-11-18 | MERGE |
| origin/banner-versions | 2025-11-19 | MERGE |
| origin/remove-webjars-locator-core-dep | 2025-11-26 | MERGE |
| origin/merge-back-7.0.5 | 2026-01-12 | MERGE |
| origin/invokeDynamicDisable | 2026-01-17 | MERGE |
| origin/deps/update-java-gradle-groovy-versions | 2026-01-27 | MERGE |
| origin/task/add-agents-md-15145 | 2026-01-30 | MERGE |
| origin/matrei-patch-1 | 2026-02-12 | MERGE |
| origin/fix/flaky-geb-tests | 2026-02-19 | MERGE |
| origin/refactor/centralize-groovydoc-plugin | 2026-02-19 | MERGE |
| origin/test/query-connection-routing | 2026-02-21 | MERGE |
| origin/micronaut-fixes-2 | 2026-02-21 | MERGE |
| origin/forgeReloadingChanges | 2026-02-23 | MERGE |
| origin/database-cleanup-feature | 2026-02-25 | MERGE |
| origin/fix-detachedcriteria-join-get-hibernate7 | 2026-02-25 | MERGE |
| origin/fix-detachedcriteria-join-get | 2026-02-25 | MERGE |
| origin/fix/where-query-bugs | 2026-02-26 | MERGE |
| origin/fix/async-promise-spec-read-timeout | 2026-03-03 | MERGE |
| origin/fix/groovy-joint-ci-stability | 2026-03-03 | MERGE |
"""

def failedBranches = []

tableData.eachLine { line ->
    if (!line.contains("|") || line.contains("---") || line.contains("Branch")) return null

    def parts = line.tokenize('|').collect { it.trim() }
    if (parts.size() < 3) return null

    def branch = parts[0].replace("origin/", "")
    def type   = parts[2]

    if (type == "MERGE" || type == "CLOSED") {
        try {
            deleteBranch(baseApiUrl, githubToken, branch)
        } catch (Exception e) {
            println "CRITICAL ERROR processing ${branch}: ${e.message}"
            failedBranches << branch
        }
    } else {
        println "SKIPPING [${type}]: ${branch}"
    }
    return null
}

if (failedBranches) {
    throw new RuntimeException("Failed to delete the following branches: ${failedBranches.join(', ')}. Check logs for details.")
}

/**
 * Removes the branch from the remote.
 */
def deleteBranch(baseUrl, token, branch) {
    println "DELETING: ${branch}"
    def url = new URL("${baseUrl}/git/refs/heads/${branch}")
    sendRequest(url, token, "DELETE", null)
}

def sendRequest(url, token, method, body) {
    HttpURLConnection conn = null
    try {
        conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout    = 30_000
        conn.requestMethod  = method
        conn.setRequestProperty("Authorization", "token ${token}")
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
        conn.setRequestProperty("User-Agent", "DeleteBranchesScript/1.0")
        if (body) {
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.withWriter { it << body }
        }

        if (conn.responseCode in [200, 201, 204]) {
            println "SUCCESS: ${conn.responseCode}"
        } else {
            def errorText = conn.errorStream?.text ?: "No error stream available"
            throw new RuntimeException("HTTP ${conn.responseCode} - ${errorText}")
        }
    } finally {
        conn?.disconnect()
    }
}
