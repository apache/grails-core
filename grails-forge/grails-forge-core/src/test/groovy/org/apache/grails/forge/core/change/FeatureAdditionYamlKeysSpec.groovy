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

import java.nio.file.Path
import org.grails.forge.application.generator.ProjectGenerator
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Unroll

/**
 * A setting the project and a feature both have in a configuration file is found as YAML and Grails
 * read the file, and not by the letters of its lines: a key in quotes is the key without them, and a
 * setting that is written with dots is the setting that is written as nested keys.
 */
class FeatureAdditionYamlKeysSpec extends AbstractFeatureAdditionSpec {

    static final String CONFIGURATION = 'grails-app/conf/application.yml'
    static final String TOP_LEVEL = 'the project and the feature each have the top-level key quartz.autoStartup in one document of this file, in different places'
    static final String SETTING = 'the project and the feature each have the setting quartz.autoStartup in one document of this file, in different places or ' +
            'written in different ways'

    @Unroll
    void "the key of the feature that the project has #written is a conflict, and nothing is written"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        File configuration = project.resolve(CONFIGURATION).toFile()
        configuration.text = mine + configuration.text
        Map<String, String> before = tree(project)

        expect: 'the file of the project is YAML that reads'
        read(configuration.text) != null

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.conflicts*.path == [CONFIGURATION]
        plan.conflicts[0].reason == reason
        plan.conflicts[0].details.size() == 1

        when:
        plan.apply()

        then:
        thrown(IllegalStateException)
        tree(project) == before

        where:
        written                    | mine                                  || reason
        'in single quotes'         | "'quartz.autoStartup': false\n"       || TOP_LEVEL
        'in double quotes'         | '"quartz.autoStartup": false\n'       || TOP_LEVEL
        'with an escaped letter'   | '"quartz.auto\\x53tartup": false\n'   || TOP_LEVEL
        'as an explicit key'       | '? quartz.autoStartup\n: false\n'     || TOP_LEVEL
        'as nested keys'           | 'quartz:\n  autoStartup: false\n'     || SETTING
        'as nested keys in a flow' | 'quartz: {autoStartup: false}\n'      || SETTING
        'as a group of settings'   | 'quartz:\n  autoStartup:\n    x: 1\n' || SETTING
    }

    @Unroll
    void "a key of the project that is another setting than the one of the feature is merged: #what"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        File configuration = project.resolve(CONFIGURATION).toFile()
        configuration.text = mine + configuration.text

        when:
        ChangePlan plan = planOf(generator, project)
        plan.apply()
        Map<String, Object> merged = read(configuration.text)

        then: 'the file reads, with the setting of the feature and that of the project'
        plan.conflicts.isEmpty()
        merged.get('quartz.autoStartup') == true
        configuration.text.startsWith(mine)

        where:
        what                                  | mine
        'the setting of one environment'      | 'mine:\n  environments:\n    development:\n      quartz:\n        autoStartup: false\n'
        'another setting of the group'        | 'quartz:\n  jdbcStore: false\n'
        'a key in quotes that is another one' | "'quartz.autoStartup2': false\n"
        'a key of a map in a list'            | 'mine:\n  - quartz.autoStartup: false\n'
    }

    void "the key in another document of the file is no conflict"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        File configuration = project.resolve(CONFIGURATION).toFile()
        configuration.text = "'quartz.autoStartup': false\n---\n" + configuration.text

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.conflicts.isEmpty()
    }

    void "a file of the project that is not YAML that reads is looked at by its lines, as far as that goes"() {
        given:
        ProjectGenerator generator = generator()
        Path project = create(generator, 'app', [])
        File configuration = project.resolve(CONFIGURATION).toFile()
        configuration.text = 'quartz.autoStartup: false\nbroken: [\n' + configuration.text

        when:
        ChangePlan plan = planOf(generator, project)

        then:
        plan.conflicts*.reason == [TOP_LEVEL]
    }

    private static Map<String, Object> read(String text) {
        LoaderOptions options = new LoaderOptions()
        options.setAllowDuplicateKeys(false)
        return new Yaml(new SafeConstructor(options)).load(text) as Map<String, Object>
    }
}
