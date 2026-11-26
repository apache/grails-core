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
package org.apache.grails.gradle.model.cli

import java.nio.file.Files

import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * What a tool is told its project is, read from the record the {@code grails} command line writes.
 * The records under {@code project-record-sample} are ones the command line wrote for an
 * application of each type, so that the reader and the writer hold to one format.
 */
class ProjectFactsSpec extends Specification {

    private static final String RECORD = '''\
        format: 1
        applicationType: web
        features:
        - name: gsp
          source: bundled-pack
        - name: views-json
          source: bundled-pack
        '''.stripIndent()

    @TempDir
    File dir

    def "the record of a web application gives its type and its features, those it has by default included"() {
        when:
        ProjectFacts facts = ProjectFacts.read(sample('web'))

        then:
        facts.recorded
        facts.applicationType == 'web'
        facts.features.containsAll(['gsp', 'scaffolding', 'gorm-hibernate5', 'spring-boot-starter-tomcat'])
        !facts.features.contains('views-json')
        facts.features.size() == 29
        facts.features.first() == 'app-name'
        facts.features.last() == 'yaml'
    }

    def "the record of a REST application gives its type and its features"() {
        when:
        ProjectFacts facts = ProjectFacts.read(sample('rest-api'))

        then:
        facts.recorded
        facts.applicationType == 'rest-api'
        facts.features.containsAll(['views-json', 'gorm-hibernate5'])
        !facts.features.contains('gsp')
        !facts.features.contains('scaffolding')
    }

    def "the features of a record cannot be changed"() {
        when:
        ProjectFacts.read(sample('web')).features << 'another'

        then:
        thrown(UnsupportedOperationException)
    }

    @Unroll
    def "a project without a record is not recorded: #title"() {
        expect:
        with(ProjectFacts.read(directory.call(dir))) {
            !recorded
            applicationType == null
            features == []
        }

        where:
        title                                   | directory
        'no directory is given'                 | { File dir -> null }
        'the directory is not there'            | { File dir -> new File(dir, 'not/there') }
        'the directory has no record'           | { File dir -> dir }
        'the record is a directory'             | { File dir -> new File(dir, '.grails/forge.lock.yml').mkdirs(); dir }
        'the directory is a file'               | { File dir -> File file = new File(dir, 'file'); file.text = RECORD; file }
    }

    @Unroll
    def "the type of application #type is read"() {
        expect:
        with(ProjectFacts.parse(RECORD.replace('applicationType: web', "applicationType: ${type}").getBytes('UTF-8'))) {
            recorded
            applicationType == type
            features == ['gsp', 'views-json']
        }

        where:
        type << ['web', 'rest-api', 'plugin', 'web-plugin', 'rest-api-plugin']
    }

    @Unroll
    def "a record that #title is no record, and nothing is thrown"() {
        expect:
        ProjectFacts.parse(content.getBytes('UTF-8')).is(ProjectFacts.NONE)

        and: 'as a file of a project'
        ProjectFacts.read(project(content)).is(ProjectFacts.NONE)

        where:
        title                                        | content
        'is empty'                                   | ''
        'is not YAML'                                | 'format: [1\n'
        'is a list'                                  | '- format: 1\n'
        'is a text'                                  | 'format\n'
        'has no format'                              | RECORD.replace('format: 1\n', '')
        'is of another format'                       | RECORD.replace('format: 1', 'format: 2')
        'has no type of application'                 | RECORD.replace('applicationType: web\n', '')
        'has a type of application that is none'     | RECORD.replace('applicationType: web', 'applicationType: desktop')
        'has a list for the type of application'     | RECORD.replace('applicationType: web', 'applicationType: [web]')
        'has no features'                            | 'format: 1\napplicationType: web\n'
        'has a mapping for the features'             | 'format: 1\napplicationType: web\nfeatures:\n  gsp: true\n'
        'has a feature that is a text'               | 'format: 1\napplicationType: web\nfeatures:\n- gsp\n'
        'has a feature without a name'               | 'format: 1\napplicationType: web\nfeatures:\n- source: bundled-pack\n'
        'has a feature with a list for a name'       | 'format: 1\napplicationType: web\nfeatures:\n- name: [gsp]\n'
        'has a feature with an upper-case name'      | RECORD.replace('name: gsp', 'name: Gsp')
        'has a feature with a name of 65 characters' | RECORD.replace('name: gsp', 'name: ' + ('a' * 65))
        'has a feature whose name starts with a hyphen' | RECORD.replace('name: gsp', 'name: "-gsp"')
        'has a feature with a line break in its name' | RECORD.replace('name: gsp', 'name: "gsp\\nviews"')
        'has more than 1024 features'                | 'format: 1\napplicationType: web\nfeatures:\n' + (0..1024).collect { "- name: f${it}\n" }.join()
        'has a key twice'                            | RECORD + 'applicationType: rest-api\n'
        'has an alias for a list'                    | 'format: 1\napplicationType: web\nfeatures: &all\n- name: gsp\nother: *all\n'
        'makes an object of a class'                 | RECORD + 'made: !!java.io.File /tmp\n'
        'is nested deeper than a record is'          | RECORD + 'deep: ' + ('[' * 40) + (']' * 40) + '\n'
        'is nested without end'                      | 'format: ' + ('[' * 20000)
        'is not UTF-8'                               | RECORD + 'other: \ud800\n'
    }

    def "what a record says beside the type and the features is not read"() {
        expect:
        with(ProjectFacts.parse((RECORD + 'later:\n  anything: [1, 2]\noptions:\n  jdk: 99\n').getBytes('UTF-8'))) {
            recorded
            applicationType == 'web'
            features == ['gsp', 'views-json']
        }
    }

    def "a name that would be a number or a truth value in YAML is the text it is, and a feature that is named twice is listed once"() {
        expect:
        ProjectFacts.parse('format: 1\napplicationType: web\nfeatures:\n- name: 12\n- name: on\n- name: 1e3\n- name: on\n'.getBytes('UTF-8')).features == ['12', 'on', '1e3']
    }

    def "a record with as many features as a record names, each with the longest name, is read"() {
        given:
        List<String> names = (1..1024).collect { String.format('f%04d', it) + ('a' * 59) }

        expect:
        ProjectFacts.parse(('format: 1\napplicationType: plugin\nfeatures:\n' + names.collect { "- name: ${it}\n" }.join()).getBytes('UTF-8')).features == names
    }

    def "a record of more than a mebibyte is no record"() {
        given:
        String large = RECORD + '# ' + ('x' * ProjectFacts.MAX_BYTES) + '\n'

        expect:
        ProjectFacts.parse(large.getBytes('UTF-8')).is(ProjectFacts.NONE)
        ProjectFacts.read(project(large)).is(ProjectFacts.NONE)

        and: 'the same record within the bound is read'
        ProjectFacts.read(project(RECORD + '# ' + ('x' * 1000) + '\n')).recorded
    }

    @IgnoreIf({ os.windows })
    def "a record that is a link is no record"() {
        given:
        File elsewhere = new File(dir, 'elsewhere.yml')
        elsewhere.text = RECORD
        File project = new File(dir, 'linked')
        new File(project, '.grails').mkdirs()
        Files.createSymbolicLink(new File(project, ProjectFacts.RECORD).toPath(), elsewhere.toPath())

        expect:
        ProjectFacts.read(project).is(ProjectFacts.NONE)
    }

    def "the record is the lock of the command line, in the directory of the project"() {
        expect:
        ProjectFacts.RECORD == '.grails/forge.lock.yml'
        ProjectFacts.FORMAT == 1
        ProjectFacts.MAX_BYTES == 1024 * 1024
        ProjectFacts.MAX_FEATURES == 1024
        ProjectFacts.APPLICATION_TYPES == ['web', 'rest-api', 'plugin', 'web-plugin', 'rest-api-plugin']
    }

    private int projects

    private File project(String record) {
        File project = new File(dir, "project${projects++}")
        File file = new File(project, ProjectFacts.RECORD)
        file.parentFile.mkdirs()
        file.setText(record, 'UTF-8')
        project
    }

    private static File sample(String type) {
        File sample = new File(ProjectFactsSpec.getResource("/project-record-sample/${type}/.grails/forge.lock.yml").toURI()).parentFile.parentFile
        assert sample.isDirectory()
        sample
    }
}
