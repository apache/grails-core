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

package org.grails.forge.build.gradle

import spock.lang.Specification
import spock.lang.Unroll

class BuildFileContributionsSpec extends Specification {

    BuildFileContributions contributions = new BuildFileContributions()

    void "nothing is rendered when nothing was contributed"() {
        expect:
        contributions.renderExtensions() == ''
        BuildFileAnchor.values().every { contributions.renderBlocks(it) == '' }
    }

    @Unroll
    void "the extension #name starts its block with its name as written"() {
        when:
        contributions.extension(name, 'enabled = true')

        then:
        contributions.renderExtensions() == name.trim() + ' {\n    enabled = true\n}\n\n'

        where:
        name << ['assets', 'tasks.withType(Test).configureEach', 'tasks.configureEach', ' grails ']
    }

    void "a task contribution configures the task by its name"() {
        when:
        contributions.task('bootRun', 'dependsOn(generateRebel)')
        contributions.task('my-task', 'enabled = false')
        contributions.task('bootRun', 'jvmArgs(rebelAgent)')

        then:
        contributions.renderExtensions() == '''\
            tasks.named('bootRun') {
                dependsOn(generateRebel)
                jvmArgs(rebelAgent)
            }

            tasks.named('my-task') {
                enabled = false
            }

            '''.stripIndent()
    }

    @Unroll
    void "the task name '#taskName' is rejected"() {
        when:
        contributions.task(taskName, 'enabled = false')

        then:
        thrown(IllegalArgumentException)

        where:
        taskName << ['', ' ', null, "it's", 'back\\slash']
    }

    void "the same body contributed twice is in the block twice"() {
        when:
        2.times { contributions.extension('assets', 'minifyJs = true') }

        then:
        contributions.renderExtensions() == 'assets {\n    minifyJs = true\n    minifyJs = true\n}\n\n'
    }

    void "the line endings and the empty first lines of a body are not kept"() {
        when:
        contributions.extension('assets', '\n\r\nminifyJs = true\r\nminifyCss = true\r\n')
        contributions.block(BuildFileAnchor.END, '\nprintln "one"\r\nprintln "two"')

        then:
        contributions.renderExtensions() == 'assets {\n    minifyJs = true\n    minifyCss = true\n}\n\n'
        contributions.renderBlocks(BuildFileAnchor.END) == 'println "one"\nprintln "two"\n\n'
    }

    void "contributions to one extension share its block, in the order they were made"() {
        when:
        contributions.extension('assets', 'minifyJs = true')
        contributions.extension('grails', 'pathingJar = true')
        contributions.extension('assets', "excludes = [\n        'webjars/**',\n]\n")

        then:
        contributions.renderExtensions() == '''\
            assets {
                minifyJs = true
                excludes = [
                        'webjars/**',
                ]
            }

            grails {
                pathingJar = true
            }

            '''.stripIndent()
    }

    void "an empty line of a body is not indented"() {
        when:
        contributions.extension('assets', 'minifyJs = true\n\nminifyCss = true')

        then:
        contributions.renderExtensions() == 'assets {\n    minifyJs = true\n\n    minifyCss = true\n}\n\n'
    }

    void "blocks are rendered at their anchor as they are, in the order they were added, each followed by an empty line"() {
        when:
        contributions.block(BuildFileAnchor.END, 'apply from: "gradle/second.gradle"\n')
        contributions.block(BuildFileAnchor.AFTER_PLUGINS, 'apply from: "gradle/docs.gradle"')
        contributions.block(BuildFileAnchor.END, 'sourceSets {\n    main { }\n}')

        then:
        contributions.renderBlocks(BuildFileAnchor.AFTER_PLUGINS) == 'apply from: "gradle/docs.gradle"\n\n'
        contributions.renderBlocks(BuildFileAnchor.AFTER_DEPENDENCIES) == ''
        contributions.renderBlocks(BuildFileAnchor.END) == 'apply from: "gradle/second.gradle"\n\nsourceSets {\n    main { }\n}\n\n'
    }

    void "a contribution made after the build file was rendered is an error"() {
        given:
        contributions.extension('assets', 'minifyJs = true')
        contributions.close()

        when:
        contributions.extension('grails', 'pathingJar = true')

        then:
        IllegalStateException e = thrown()
        e.message == "The build file is already rendered, so 'grails' cannot be added to it. " +
                'A feature that contributes to the build file must be applied before the build feature (FeaturePhase.BUILD).'

        when:
        contributions.block(BuildFileAnchor.END, 'println "late"')

        then:
        thrown(IllegalStateException)

        and: 'what was contributed in time is still rendered'
        contributions.renderExtensions() == 'assets {\n    minifyJs = true\n}\n\n'
    }

    @Unroll
    void "the extension '#name' with the body '#body' is rejected"() {
        when:
        contributions.extension(name, body)

        then:
        thrown(IllegalArgumentException)

        where:
        name     | body
        ''       | 'enabled = true'
        ' '      | 'enabled = true'
        null     | 'enabled = true'
        'assets' | ''
        'assets' | ' \n'
        'assets' | null
    }

    void "a block needs an anchor"() {
        when:
        contributions.block(null, 'println "nowhere"')

        then:
        thrown(NullPointerException)
    }

    void "a blank block is rejected"() {
        when:
        contributions.block(BuildFileAnchor.END, ' ')

        then:
        thrown(IllegalArgumentException)
    }
}
