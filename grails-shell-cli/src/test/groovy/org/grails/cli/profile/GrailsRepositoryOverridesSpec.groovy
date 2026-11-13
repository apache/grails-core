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

import spock.lang.Specification
import spock.lang.Unroll

class GrailsRepositoryOverridesSpec extends Specification {

    def cleanup() {
        System.clearProperty('grails.repo.url')
    }

    def 'no overrides are returned when nothing is configured'() {
        expect:
        GrailsRepositoryOverrides.configuredOverrides.empty
    }

    def 'overrides are split on semicolons, trimmed and validated'() {
        given:
        System.setProperty('grails.repo.url', ' https://repo-one.example/releases ; ; /tmp/local-repo ')

        expect:
        GrailsRepositoryOverrides.configuredOverrides == ['https://repo-one.example/releases', '/tmp/local-repo']
    }

    @Unroll
    def '#description is classified as a local repository'() {
        expect:
        GrailsRepositoryOverrides.isLocalRepository(overrideUrl)

        and: 'validation passes it through unchanged'
        GrailsRepositoryOverrides.validateOverrideRepository(overrideUrl) == overrideUrl

        where:
        description                                    | overrideUrl
        'a plain unix path'                            | '/tmp/local-repo/releases'
        'a path containing spaces'                     | '/tmp/local repo/releases'
        'a path with an interior scheme separator'     | '/tmp/cache://local repo/releases'
        'a file URI'                                   | 'file:///tmp/local-repo'
        'a Windows absolute backslash path'            | 'C:\\Users\\me\\.m2\\repository'
        'a Windows absolute forward-slash path'        | 'C:/Users/me/.m2/repository'
        'a Windows drive-relative path'                | 'C:repo/releases'
        'a Windows path containing spaces'             | 'C:my repo\\releases'
    }

    def 'the Gradle repository aliases are recognized and pass validation'() {
        expect:
        GrailsRepositoryOverrides.isRepositoryAlias(alias)
        GrailsRepositoryOverrides.validateOverrideRepository(alias) == alias

        where:
        alias << ['mavenLocal()', 'mavenCentral()']
    }

    def 'the Gradle repository aliases resolve to the locations they stand for'() {
        expect:
        GrailsRepositoryOverrides.resolveRepositoryAlias('mavenLocal()') ==
                [System.getProperty('user.home'), '.m2', 'repository'].join(File.separator)
        GrailsRepositoryOverrides.resolveRepositoryAlias('mavenCentral()') == 'https://repo1.maven.org/maven2'

        and: 'a non-alias value passes through unchanged'
        GrailsRepositoryOverrides.resolveRepositoryAlias('/tmp/local-repo') == '/tmp/local-repo'
    }

    def 'an HTTPS repository override passes validation'() {
        expect:
        GrailsRepositoryOverrides.validateOverrideRepository('https://localhost/releases') == 'https://localhost/releases'
    }

    def 'a non-HTTPS remote repository override is rejected regardless of case'() {
        when:
        GrailsRepositoryOverrides.validateOverrideRepository('HTTP://localhost/releases')

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Remote GRAILS_REPO_URL repositories must use HTTPS: HTTP://localhost/releases'
    }

    def 'a single-letter scheme with an authority is a remote URL, not a drive letter'() {
        when:
        GrailsRepositoryOverrides.validateOverrideRepository('c://host/releases')

        then:
        def e = thrown(IllegalArgumentException)
        e.message == 'Remote GRAILS_REPO_URL repositories must use HTTPS: c://host/releases'
    }

    def 'a malformed URL-shaped override is rejected rather than treated as a local path'() {
        when:
        GrailsRepositoryOverrides.validateOverrideRepository('https://repo example/releases')

        then:
        def e = thrown(IllegalArgumentException)
        e.message.startsWith('Invalid GRAILS_REPO_URL repository:')
    }

    def 'a rejected override fails the whole configuration read'() {
        given:
        System.setProperty('grails.repo.url', '/tmp/local-repo;http://localhost/releases')

        when:
        GrailsRepositoryOverrides.configuredOverrides

        then:
        thrown(IllegalArgumentException)
    }

}
