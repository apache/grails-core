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
package org.apache.grails.core.cli

import java.util.concurrent.TimeUnit

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

/**
 * A tool is told what its project is by {@code executionContext.project}, read from the record the
 * {@code grails} command line keeps in the directory of the project, whatever started the tool: here
 * the JVM the command line starts for a standalone tool, in the directory of a project with a record
 * the command line wrote, without one, and with one that is not one.
 */
class ExecutionContextProjectSpec extends Specification {

    private static final int TIMEOUT_SECONDS = 120

    // records the command line wrote for a web and a REST application, which the reader is tested with
    private static final String SAMPLES = 'grails-gradle/model/src/test/resources/project-record-sample'

    private static final String NOT_RECORDED = 'RECORDED=false TYPE=null FEATURES=[] SAME=true'

    @Shared
    File samples = findSamples()

    @TempDir
    File dir

    @Unroll
    def "the record the command line wrote for a #type application gives its type and all its features"() {
        given:
        File record = new File(samples, "${type}/.grails/forge.lock.yml")

        when:
        Map result = run(project(record.text))
        List<String> features = featuresOf(record)

        then:
        result.exit == 0
        result.out.readLines() == ["RECORDED=true TYPE=${type} FEATURES=${features} SAME=true".toString()]

        and: 'which tells a web application from a REST one'
        features.contains(has)
        !features.contains(hasNot)

        where:
        type       | has          | hasNot
        'web'      | 'gsp'        | 'views-json'
        'rest-api' | 'views-json' | 'gsp'
    }

    def "a project without a record is not recorded, has no type and no feature"() {
        when:
        Map result = run(project(null))

        then:
        result.exit == 0
        result.out.readLines() == [NOT_RECORDED]
    }

    @Unroll
    def "a record that is #what is no record"() {
        when:
        Map result = run(project(content))

        then:
        result.exit == 0
        result.out.readLines() == [NOT_RECORDED]

        where:
        what                                    | content
        'not YAML'                              | 'format: 1\napplicationType: web\nfeatures: [ - name: gsp'
        'of another format'                     | 'format: 2\napplicationType: web\nfeatures:\n- name: gsp\n'
        'of a type of application there is not' | 'format: 1\napplicationType: desktop\nfeatures:\n- name: gsp\n'
        'with a feature that is no name'        | 'format: 1\napplicationType: web\nfeatures:\n- name: \'gsp; rm -rf\'\n'
        'larger than a record is'               | 'format: 1\napplicationType: web\nfeatures:\n- name: gsp\n' + ('#' * (1024 * 1024)) + '\n'
        'empty'                                 | ''
    }

    def "a record that is a directory is no record"() {
        given:
        File project = project(null)
        new File(project, '.grails/forge.lock.yml').mkdirs()

        expect:
        run(project).out.readLines() == [NOT_RECORDED]
    }

    // a project with the record, or without one for null
    private File project(String record) {
        File project = new File(dir, 'project')
        project.mkdirs()
        if (record != null) {
            File file = new File(project, '.grails/forge.lock.yml')
            file.parentFile.mkdirs()
            file.setText(record, 'UTF-8')
        }
        project
    }

    // the names of the features of a record, in the order it has them
    private static List<String> featuresOf(File record) {
        record.readLines().findAll { String line -> line.startsWith('- name: ') }.collect { String line -> line.substring('- name: '.length()).trim() }
    }

    private static File findSamples() {
        for (File directory = new File('').absoluteFile; directory != null; directory = directory.parentFile) {
            File samples = new File(directory, SAMPLES)
            if (samples.isDirectory()) {
                return samples
            }
        }
        throw new IllegalStateException("${SAMPLES} is not in a directory above ${new File('').absolutePath}")
    }

    // the tool run as the command line runs a standalone tool, in the directory of the project
    private Map run(File project) {
        File registrations = new File(dir, 'registrations')
        new File(registrations, 'META-INF').mkdirs()
        new File(registrations, 'META-INF/grails-cli.factories').text = "${GrailsTool.name}=${ProjectSpecTool.name}\n"
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', registrations.path + File.pathSeparator + System.getProperty('java.class.path'),
                                "-Dbase.dir=${project.path}".toString(),
                                StandaloneToolRunner.name, 'project-spec']
        File out = new File(dir, 'out.txt')
        File err = new File(dir, 'err.txt')
        Process process = new ProcessBuilder(command).directory(project).redirectOutput(out).redirectError(err).start()
        boolean ended = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly().waitFor()
        }
        assert ended
        [exit: process.exitValue(), out: out.text, err: err.text]
    }
}

class ProjectSpecTool implements StandaloneTool, ToolSupport {

    boolean handle() {
        ProjectInfo project = executionContext.project
        println "RECORDED=${project.recorded} TYPE=${project.applicationType} FEATURES=${project.features} SAME=${executionContext.project.is(project)}"
        true
    }
}
