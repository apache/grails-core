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
import spock.lang.Unroll

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

        and: 'the guide says 3 of 14: the features that have a top-level key of their own, which is written after the sections every application has'
        end.size() * 4 <= changing('grails-app/conf/application.yml').size()
    }

    @Unroll
    void "one change of the user to #file is in the way of a feature no more often than it was"() {
        given:
        List<String> lines = generated.get(file).readLines()
        int inserted = 0
        int removed = 0
        int changed = 0
        int emptyLines = 0

        when: 'for every feature that changes the file: a line added at every place, every line removed, every line changed, an empty line added at every place'
        (0..lines.size()).each { int at ->
            inserted += conflicting(file, lines.take(at) + [MINE + ' // the line of the user'] + lines.drop(at)).size()
        }
        (0..<lines.size()).each { int at ->
            removed += conflicting(file, lines.take(at) + lines.drop(at + 1)).size()
            changed += conflicting(file, lines.take(at) + [lines[at] + ' // mine'] + lines.drop(at + 1)).size()
            emptyLines += conflicting(file, lines.take(at) + [''] + lines.drop(at)).size()
        }
        int places = (lines.size() + 1) * changing(file).size()
        println "${file}: ${lines.size()} lines, ${changing(file).size()} features change it; conflicts of ${places}: a line added ${inserted}, " +
                "a line removed ${removed}, a line changed ${changed}, an empty line added ${emptyLines}"

        then: 'the numbers of the day the rule of the merge was set: a change to the merge that raises one of them makes it worse for those who use it'
        inserted <= mostInserted
        removed <= mostRemoved
        changed <= mostChanged
        emptyLines <= mostEmptyLines

        // build.gradle: 123, 176, 180 and 125 until gorm-neo4j replaced the two lines of grails-bom with those of grails-neo4j-bom,
        // which made a change next to them one more feature's conflict; the merge was not changed. 129, 183, 189 and 131 until
        // cache-ehcache, which declared one line, the plugin, declared two at two places, grails-cache and Ehcache; the merge was not changed.
        // 130, 185, 191 and 132 until cache-ehcache declared jaxb-runtime among the runtimeOnly lines, a third place; the merge was not changed
        where:
        file                              || mostInserted | mostRemoved | mostChanged | mostEmptyLines
        'build.gradle'                    || 131          | 187         | 193         | 133
        'grails-app/conf/application.yml' || 43           | 69          | 69          | 40
        'README.md'                       || 70           | 101         | 101         | 71
    }

    @Unroll
    void "a change of a feature to #file that was already made by hand is merged with the rest of what the feature changes"() {
        given:
        List<String> lines = generated.get(file).readLines()
        int cases = 0
        List<String> conflicts = []
        List<String> otherResults = []

        when: 'for every feature that changes the file in more than one place: each of those changes made by hand, and nothing else'
        changing(file).each { String name ->
            List<String> withIt = withFeature.get(name).get(file).readLines()
            List<int[]> changes = ThreeWayMerge.changes(lines, withIt)
            if (changes.size() > 1) {
                changes.each { int[] change ->
                    List<String> mine = lines.take(change[0]) + withIt.subList(change[2], change[3]) + lines.drop(change[1])
                    ThreeWayMerge merge = ThreeWayMerge.of(generated.get(file).getBytes(StandardCharsets.UTF_8), (mine.join('\n') + '\n').getBytes(StandardCharsets.UTF_8),
                            withFeature.get(name).get(file).getBytes(StandardCharsets.UTF_8))
                    cases++
                    if (merge.conflicts > 0) {
                        conflicts << name
                    } else if (merge.merged != withFeature.get(name).get(file).getBytes(StandardCharsets.UTF_8)) {
                        otherResults << name
                    }
                }
            }
        }
        println "${file}: one of the changes of a feature made by hand, ${cases} cases: ${conflicts.size()} conflicts ${conflicts.unique()}"

        then:
        cases >= atLeast
        conflicts == []
        otherResults == []

        where:
        file                              || atLeast
        'build.gradle'                    || 40
        'grails-app/conf/application.yml' || 15
        'README.md'                       || 10
    }

    void "a line added at the end of the build file is in the way of no feature"() {
        expect:
        conflicting('build.gradle', generated.get('build.gradle').readLines() + [MINE]) == []
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
