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
package grails.doc.git

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Specification
import spock.lang.TempDir

class FetchTagsTaskSpec extends Specification {

    @TempDir
    File projectDir

    void setup() {
        String classpath = System.getProperty('docsCoreClasspath').split(File.pathSeparator)
                .collect { "'${it.replace('\\', '/')}'" }.join(', ')
        new File(projectDir, 'settings.gradle').text = "rootProject.name = 'tags-fixture'"
        new File(projectDir, 'gradle.properties').text = '''
            org.gradle.configuration-cache=true
            org.gradle.configuration-cache.problems=fail
        '''.stripIndent()
        new File(projectDir, 'build.gradle').text = """
            buildscript {
                dependencies {
                    classpath files(${classpath})
                }
            }

            import grails.doc.git.FetchTagsTask

            version = '1.2.3'

            tasks.register('fetchTags', FetchTagsTask)
        """
    }

    void 'the tags are listed newest first, and listed again by a build reusing the configuration cache entry'() {
        given:
        commitAndTag('2026-01-01T12:00:00Z', 'v1.0.0')

        when:
        run('fetchTags')

        then:
        tags() == 'v1.0.0'

        when: 'a release is tagged since'
        commitAndTag('2026-02-01T12:00:00Z', 'v1.1.0')
        BuildResult result = run('fetchTags')

        then:
        result.output.contains('Configuration cache entry reused')
        result.task(':fetchTags').outcome == TaskOutcome.SUCCESS
        tags() == 'v1.1.0\nv1.0.0'
    }

    void 'outside a git repository the version of the project is the tag'() {
        when:
        run('fetchTags')

        then:
        tags() == 'v1.2.3'
    }

    private String tags() {
        new File(projectDir, 'build/git-tags.txt').text
    }

    private BuildResult run(String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(arguments.toList() + ['--stacktrace'])
                .build()
    }

    private void commitAndTag(String date, String tag) {
        if (!new File(projectDir, '.git').exists()) {
            git([:], 'init', '-q')
        }
        Map<String, String> dates = [GIT_AUTHOR_DATE: date, GIT_COMMITTER_DATE: date]
        git(dates, '-c', 'user.name=Test', '-c', 'user.email=test@example.org', '-c', 'commit.gpgsign=false',
                'commit', '--allow-empty', '-q', '-m', tag)
        git(dates, 'tag', tag)
    }

    private void git(Map<String, String> environment, String... arguments) {
        ProcessBuilder builder = new ProcessBuilder(['git'] + arguments.toList())
                .directory(projectDir)
                .redirectErrorStream(true)
        builder.environment().putAll(environment)
        Process process = builder.start()
        String output = process.inputStream.text
        assert process.waitFor() == 0: "git ${arguments.join(' ')} failed: ${output}"
    }
}
