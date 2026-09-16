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

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class PostFileSpec extends Specification {
    @TempDir
    Path temporaryDirectory

    def "post file sends its contents verbatim to the comment poster"() {
        given:
        Path report = write('report.md', 'already marked\n\n<!-- grails-jmh-benchmark -->')
        CommentPoster poster = Mock()

        when:
        int result = JmhCompare.run(['--post-file', report.toString(), '--repo', 'apache/grails-core', '--pr-number', '42'] as String[], poster, [GITHUB_TOKEN: 'token'])

        then:
        result == 0
        1 * poster.post('already marked\n\n<!-- grails-jmh-benchmark -->', 'apache/grails-core', '42', 'token')
    }

    def "comment body appends the marker when absent"() {
        expect:
        GitHubComments.commentBody('report') == 'report\n\n<!-- grails-jmh-benchmark -->'
    }

    def "comment body truncates with the exact suffix"() {
        when:
        String body = GitHubComments.commentBody('x' * 70000)

        then:
        body.length() == GitHubComments.MAX_BODY
        body.endsWith(GitHubComments.TRUNCATION)
    }

    def "post file skips when the token is absent"() {
        given:
        CommentPoster poster = Mock()

        when:
        int result = JmhCompare.run(['--post-file', write('report.md', 'report').toString(), '--repo', 'apache/grails-core', '--pr-number', '42'] as String[], poster, [:])

        then:
        result == 0
        0 * poster._
    }

    def "post file skips when the repository is absent"() {
        given:
        CommentPoster poster = Mock()

        when:
        int result = JmhCompare.run(['--post-file', write('report.md', 'report').toString(), '--pr-number', '42'] as String[], poster, [GITHUB_TOKEN: 'token'])

        then:
        result == 0
        0 * poster._
    }

    def "post file skips when the pull request number is absent"() {
        given:
        CommentPoster poster = Mock()

        when:
        int result = JmhCompare.run(['--post-file', write('report.md', 'report').toString(), '--repo', 'apache/grails-core'] as String[], poster, [GITHUB_TOKEN: 'token'])

        then:
        result == 0
        0 * poster._
    }

    def "post file rejects a simultaneous head file"() {
        expect:
        JmhCompare.run(['--post-file', write('report.md', 'report').toString(), '--head', 'head.json'] as String[], Mock(CommentPoster), [:]) == 2
    }

    def "latest matching comment id prefers the newest bot report"() {
        given:
        List comments = [
                [id: 10, body: 'old <!-- grails-jmh-benchmark -->', user: [login: 'github-actions[bot]']],
                [id: 11, body: 'human note without marker', user: [login: 'alice']],
                [id: 12, body: 'new <!-- grails-jmh-benchmark -->', user: [login: 'github-actions[bot]']],
        ]

        expect:
        GitHubComments.latestMatchingCommentId(comments) == 12L
    }

    def "marker ownership requires the GitHub Actions bot login"() {
        expect:
        GitHubComments.latestMatchingCommentId([[id: 12, body: 'report <!-- grails-jmh-benchmark -->', user: author]]) == null

        where:
        author << [null, [:], [login: 'someone-else'], 'github-actions[bot]']
    }

    private Path write(String name, String content) {
        Path path = temporaryDirectory.resolve(name)
        Files.writeString(path, content)
        path
    }
}
