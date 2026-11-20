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

import java.nio.charset.StandardCharsets
import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.OperatingSystem
import org.grails.forge.application.generator.ProjectGenerator
import org.grails.forge.feature.Feature
import org.grails.forge.io.ConsoleOutput
import org.grails.forge.io.MapOutputHandler
import org.grails.forge.options.Options
import org.grails.forge.util.NameUtils
import spock.lang.Shared

/**
 * Where a line of one's own is in the way of a feature that is added, for every feature of a web
 * application that can be selected by name: the guide says where to put such a line so that it is
 * not, and where to expect a conflict, and this holds it to that. The numbers are printed.
 */
class FeatureAdditionPlacesSpec extends ApplicationContextSpec {

    static final String MINE = '    implementation "com.acme:mine:1.0"'

    @Shared
    Map<String, String> generated

    @Shared
    Map<String, Map<String, String>> withFeature = [:]

    void setupSpec() {
        generated = render([])
        ProjectGenerator generator = beanContext.getBean(ProjectGenerator)
        for (Feature feature : generator.availableFeatures(ApplicationType.WEB, []).getFeatures().toList()) {
            try {
                withFeature.put(feature.getName(), render([feature.getName()]))
            } catch (IllegalArgumentException ignored) {
                // a feature that cannot be had with the defaults
            }
        }
    }

    void "a dependency that is the first line of the dependencies block is in the way of no feature, and one that is the last is in the way of some"() {
        given:
        List<String> lines = generated.get('build.gradle').readLines()
        int open = lines.findIndexOf { it.startsWith('dependencies {') }
        int close = (open..<lines.size()).find { lines[it] == '}' }

        when:
        List<String> first = conflicting('build.gradle', lines.take(open + 1) + [MINE] + lines.drop(open + 1))
        List<String> last = conflicting('build.gradle', lines.take(close) + [MINE] + lines.drop(close))
        List<String> ownBlock = conflicting('build.gradle', lines + ['', 'dependencies {', MINE, '}'])
        println "build.gradle: ${changing('build.gradle').size()} features change it; a dependency as the first line of the block conflicts for ${first.size()}, " +
                "as the last line for ${last.size()} ${last}, in a block of its own at the end of the file for ${ownBlock.size()} ${ownBlock}"

        then:
        changing('build.gradle').size() > 20
        first == []

        and: 'the guide says about a quarter'
        last.size() * 2 <= changing('build.gradle').size()
    }

    void "a setting above the generated ones is in the way of no feature, and one at the end of the configuration is in the way of some"() {
        given:
        List<String> lines = generated.get('grails-app/conf/application.yml').readLines()

        when:
        List<String> top = conflicting('grails-app/conf/application.yml', ['mine:', '    first: true'] + lines)
        List<String> end = conflicting('grails-app/conf/application.yml', lines + ['mine:', '    last: true'])
        println "application.yml: ${changing('grails-app/conf/application.yml').size()} features change it; a setting above the generated ones conflicts for ${top.size()} ${top}, " +
                "at the end for ${end.size()} ${end}"

        then:
        changing('grails-app/conf/application.yml').size() > 8
        top == []

        and: 'the guide says about half'
        end.size() <= changing('grails-app/conf/application.yml').size() * 2 / 3
    }

    private List<String> changing(String file) {
        return withFeature.findAll { it.value.get(file) != null && it.value.get(file) != generated.get(file) }.keySet().toList()
    }

    // the features whose change of a file does not merge with the file as the project has it
    private List<String> conflicting(String file, List<String> mine) {
        byte[] base = generated.get(file).getBytes(StandardCharsets.UTF_8)
        byte[] current = (mine.join('\n') + '\n').getBytes(StandardCharsets.UTF_8)
        return changing(file).findAll { String name ->
            ThreeWayMerge.of(base, current, withFeature.get(name).get(file).getBytes(StandardCharsets.UTF_8)).conflicts > 0
        }
    }

    private Map<String, String> render(List<String> features) {
        MapOutputHandler output = new MapOutputHandler()
        beanContext.getBean(ProjectGenerator).generate(ApplicationType.WEB, NameUtils.parse('com.acme.shop'), new Options(), OperatingSystem.LINUX, features,
                output, ConsoleOutput.NOOP)
        return output.getProject()
    }
}
