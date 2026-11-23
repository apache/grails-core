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
package org.apache.grails.forge.core.change

import java.nio.file.Files
import java.nio.file.Path
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.FeatureSelectionException
import org.grails.forge.io.ConsoleOutput
import spock.lang.Unroll

/**
 * Why features are not added to a project, and why it is not generated again, as the codes a tool
 * reads: each refusal of a request and each project that cannot be worked on has its own, and a
 * failure that is none of those has none.
 */
class FeatureAdditionReasonsSpec extends AbstractFeatureAdditionSpec {

    @Unroll
    void "a request #what is refused with the reason #reason"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', created)
        Map<String, String> before = tree(project)

        when:
        addition(generator).plan(project, features, OperatingSystem.LINUX, ConsoleOutput.NOOP, replace)

        then:
        FeatureRequestException e = thrown()
        e instanceof FeatureSelectionException
        e.reason == reason
        tree(project) == before

        where:
        what                                                  | created | features                  | replace || reason
        'of two features of one group'                        | []      | ['h2', 'postgres']        | true    || FeatureRequestException.CONTRADICTORY_REQUEST
        'of two features of one group, one of them a default' | []      | ['sitemesh3', 'grails-layout'] | false || FeatureRequestException.CONTRADICTORY_REQUEST
        'that replaces a feature that was asked for'          | ['h2']  | ['postgres']              | false   || FeatureRequestException.REPLACE_REQUIRED
        'that replaces what another feature requires'         | ['grails-spring-security', 'grails-spring-security-ui'] | ['spring-boot-starter-security'] | true || FeatureRequestException.REQUIRED_BY_ANOTHER_FEATURE
        'by the coordinates of a plugin'                      | []      | ['com.acme:audit:1.0']    | false   || FeatureRequestException.PLUGIN_COORDINATES
    }

    void "a feature that does not exist is refused by the selection, with no reason of adding features"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])

        when:
        planOf(generator, project, ['no-such-feature'])

        then:
        FeatureSelectionException e = thrown()
        !(e instanceof FeatureRequestException)
    }

    void "a project that is half changed by an interrupted change is refused with the reason interrupted-change"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        try {
            planOf(generator, project).apply { String path ->
                if (path == 'build.gradle') {
                    throw new Stopped()
                }
            }
        } catch (Stopped ignored) {
        }

        when:
        planOf(generator, project)

        then:
        ProjectChangeException e = thrown()
        e.message == INTERRUPTED
        e.reason == ProjectChangeException.INTERRUPTED_CHANGE

        when:
        addition(generator).regenerate(project, OperatingSystem.LINUX)

        then:
        ProjectChangeException again = thrown()
        again.reason == ProjectChangeException.INTERRUPTED_CHANGE
    }

    void "a baseline the generator does not give again is refused with the reason baseline-differs"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        rewriteGenerated(project, 'README.md', '# another readme\n')

        when:
        addition(generator).regenerate(project, OperatingSystem.LINUX)

        then:
        ProjectChangeException e = thrown()
        e.reason == ProjectChangeException.BASELINE_DIFFERS

        when:
        planOf(generator, project)

        then:
        ProjectChangeException again = thrown()
        again.reason == ProjectChangeException.BASELINE_DIFFERS
    }

    void "a record the generator fails on is refused with the reason not-regenerable"() {
        given:
        ProjectGenerator working = generator()
        Path project = create(working, 'app', [])
        ProjectGenerator failing = generator { GeneratorContext context -> throw new IllegalStateException('the generator is broken') }

        when:
        addition(failing).regenerate(project, OperatingSystem.LINUX)

        then:
        ProjectChangeException e = thrown()
        e.message == 'The application cannot be generated again as it was recorded: the generator is broken'
        e.reason == ProjectChangeException.NOT_REGENERABLE
    }

    void "a change that cannot write a file has no reason of its own"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        ChangePlan plan = planOf(generator, project)
        Files.writeString(project.resolve('build.gradle'), '// changed after the plan\n')

        when:
        plan.apply()

        then:
        ProjectChangeException e = thrown()
        e.reason == null
    }
}
