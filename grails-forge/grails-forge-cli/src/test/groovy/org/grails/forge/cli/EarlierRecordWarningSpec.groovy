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
package org.grails.forge.cli

import groovy.json.JsonSlurper
import org.grails.forge.cli.command.AbstractFeatureAddCommandSpec
import spock.lang.Unroll

/**
 * The warning that a {@code grails-forge-cli.yml} beside the {@code .grails} of a project names other
 * features: which command lines say it, and which do not. It is said, once and on the standard error,
 * by the commands that go by the record, and by no other.
 */
class EarlierRecordWarningSpec extends AbstractFeatureAddCommandSpec {

    static final String WARNING = 'grails-forge-cli.yml and .grails do not say the same about the features of the project; the commands go by .grails, the record of how ' +
            'the project was generated. Remove grails-forge-cli.yml once .grails says what the project is, which project show tells, unless a tool of yours reads the file'

    File project

    void setup() {
        assert ForgeCli.execute(beanContext, 'create-app', 'example.shop') == 0
        project = new File(dir, 'shop')
        List<String> features = (new File(project, '.grails/forge.lock.yml').getText('UTF-8') =~ /(?m)^- name: (.+)$/).collect { List<String> match -> match[1] }
        new File(project, 'grails-forge-cli.yml').setText("applicationType: web\ndefaultPackage: example\nfeatures: [grails-quartz, ${features.join(', ')}]\n", 'UTF-8')
        // the build of the project has listed the commands of the application, of which there are none
        new File(project, '.grails/cache').mkdirs()
        new File(project, '.grails/cache/tool-catalog.json').text = '{"format":1,"complete":true,"diagnostics":[],"tools":[]}'
        System.setProperty('user.dir', project.absolutePath)
        reset()
    }

    @Unroll
    void "#arguments goes by the record, and says once that the earlier record names other features"() {
        when:
        ForgeCli.execute(beanContext, arguments as String[])

        then: 'on the standard error, with what to do about it, and not among what the command writes'
        err.toString().readLines().findAll { String line -> line.contains('do not say the same') } == ['| Warning ' + WARNING]
        !out.toString().contains('do not say the same')

        where:
        arguments << [['create-controller', 'Book'],
                      ['create-domain-class', 'Book'],
                      ['install-templates'],
                      ['feature', 'add', 'cache', '--dry-run'],
                      ['feature', 'add', 'cache', '--dry-run', '--json'],
                      ['feature', 'diff', 'cache'],
                      ['feature', 'show', 'cache'],
                      ['feature', 'find', 'cache'],
                      ['project', 'show'],
                      ['project', 'show', '--json'],
                      ['project', 'regenerate', '--diff']]
    }

    @Unroll
    void "#arguments does not go by the record, and says nothing of the earlier one"() {
        when:
        ForgeCli.execute(beanContext, arguments as String[])

        then:
        !err.toString().contains('do not say the same')
        !out.toString().contains('do not say the same')

        where:
        arguments << [['plugin', 'find', 'cache', '--plugin-index', 'none'],
                      ['plugin', 'list', '--plugin-index', 'none'],
                      ['stop-app'],
                      ['help'],
                      ['help', 'compile'],
                      ['create-controller', '--help'],
                      ['feature', 'add', '--help'],
                      ['project', 'migrate', '--dry-run'],
                      ['create-app', 'example.other']]
    }

    void "project health has it as a finding, and help --json as a diagnostic of the directory"() {
        when:
        ForgeCli.execute(beanContext, 'project', 'health')

        then:
        out.toString().contains('leftover.earlier-record-differs')
        !err.toString().contains('do not say the same')

        when:
        reset()
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map described = (Map) new JsonSlurper().parseText(out.toString())

        then:
        exit == 0
        err.toString().isEmpty()
        described.diagnostics == [WARNING]
    }

    void "nothing is said when the two records name the same features, and nothing of a record of the earlier kind alone"() {
        given:
        File earlier = new File(project, 'grails-forge-cli.yml')
        earlier.setText(earlier.getText('UTF-8').replace('grails-quartz, ', ''), 'UTF-8')

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'show')

        then:
        exit == 0
        err.toString().isEmpty()

        when:
        reset()
        exit = ForgeCli.execute(beanContext, 'feature', 'show', 'cache')

        then:
        exit == 0
        err.toString().isEmpty()
    }
}
