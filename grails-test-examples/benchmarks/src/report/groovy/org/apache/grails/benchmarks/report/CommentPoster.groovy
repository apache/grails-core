/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.grails.benchmarks.report

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileStatic

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@CompileStatic
interface CommentPoster {

    void post(String report, String repo, String prNumber, String token)
}

@CompileStatic
class GitHubComments implements CommentPoster {

    static final int MAX_BODY = 65000
    static final String TRUNCATION = '\n\n_Report truncated. The full report is available in the workflow artifacts._\n\n<!-- grails-jmh-benchmark -->'
    private final HttpClient client
    private final String apiBase

    GitHubComments() {
        this('https://api.github.com')
    }

    GitHubComments(String apiBase) {
        this.apiBase = apiBase
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build()
    }

    @Override
    void post(String report, String repo, String prNumber, String token) {
        String body = commentBody(report)
        Long commentId = null
        for (int page = 1; page <= 100; page++) {
            Object response = request(
                    "${apiBase}/repos/${repo}/issues/${prNumber}/comments?per_page=100&page=${page}",
                    token,
                    'GET',
                    null)
            if (!(response instanceof List)) {
                break
            }
            List<?> comments = (List<?>) response
            Long pageMatch = latestMatchingCommentId(comments)
            if (pageMatch != null) {
                commentId = pageMatch
            }
            if (comments.size() < 100) {
                break
            }
        }
        String endpoint = commentId == null
                ? "${apiBase}/repos/${repo}/issues/${prNumber}/comments"
                : "${apiBase}/repos/${repo}/issues/comments/${commentId}"
        request(endpoint, token, commentId == null ? 'POST' : 'PATCH', JsonOutput.toJson([body: body]))
    }

    /**
     * Keep the last matching comment on a page so duplicate marker comments update the
     * newest bot report (GitHub returns comments oldest-first).
     */
    static Long latestMatchingCommentId(List<?> comments) {
        Long commentId = null
        for (Object comment : comments) {
            if (!(comment instanceof Map) || !markerComment((Map<String, Object>) comment)) {
                continue
            }
            Object id = ((Map<String, Object>) comment).get('id')
            if (id instanceof Number) {
                commentId = ((Number) id).longValue()
            }
        }
        return commentId
    }

    static String commentBody(String report) {
        String body = report.contains(ReportRenderer.MARKER)
                ? report
                : report + '\n\n' + ReportRenderer.MARKER
        if (body.length() > MAX_BODY) {
            body = body.substring(0, MAX_BODY - TRUNCATION.length()) + TRUNCATION
        }
        return body
    }

    private static boolean markerComment(Map<String, Object> comment) {
        Object body = comment.get('body')
        Object user = comment.get('user')
        Object login = user instanceof Map ? ((Map<String, Object>) user).get('login') : null
        return body instanceof String && ((String) body).contains(ReportRenderer.MARKER) && login == 'github-actions[bot]'
    }

    private Object request(String url, String token, String method, String body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(15)).header('Accept', 'application/vnd.github+json').header('Authorization', "Bearer ${token}").header('Content-Type', 'application/json').header('X-GitHub-Api-Version', '2022-11-28')
        HttpRequest request = body == null ? builder.method(method, HttpRequest.BodyPublishers.noBody()).build() : builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build()
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() >= 400) throw new IOException("GitHub HTTP ${response.statusCode()}")
        return new JsonSlurper().parseText(response.body())
    }
}
