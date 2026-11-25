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
package org.grails.forge.cli.command

import groovy.json.JsonSlurper
import org.grails.forge.cli.ForgeCli

/**
 * What the commands say of an application whose record {@code project migrate} wrote, see
 * {@link EarlierApplication}: the name they give as an example is the name of the application, the
 * package is not compared with one the command they print does not give, and the lines a feature
 * would have added to a file the application does not have are the lines that say something.
 */
class AdoptedRecordTextsSpec extends AbstractFeatureAddCommandSpec {

    void "project migrate names the application itself where it says how to say it otherwise"() {
        given: 'the application legacy in the package com.example'
        EarlierApplication.copy(EarlierApplication.GRAILS_8_SHELL, dir)
        EarlierApplication.upgrade(dir)

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'migrate')
        String closing = out.toString().readLines().find { String line -> line.contains('To say it otherwise') }

        then:
        exit == 0
        closing.contains(' project migrate --force with the name and the package (com.example.legacy), --type, --jdk, --servlet, --data, --reloading, ')
        !out.toString().contains('com.example.shop')
    }

    void "project show does not say that the package differs from a default: it is given with the name, as the command it prints has it"() {
        given:
        EarlierApplication.copy(EarlierApplication.GRAILS_8_SHELL, dir)
        EarlierApplication.upgrade(dir)
        assert ForgeCli.execute(beanContext, 'project', 'migrate') == 0
        reset()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'show')
        List<String> lines = out.toString().readLines()

        then: 'the package is said as the name is, and the command gives it'
        exit == 0
        lines.any { String line -> line ==~ / +package +com\.example/ }
        !lines.any { String line -> line.contains('package') && line.contains('differs from the current default') }
        lines.any { String line -> line.contains(' create-app com.example.legacy') }

        when:
        reset()
        exit = ForgeCli.execute(beanContext, 'project', 'show', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        exit == 0
        (document.effectiveOptions as List<Map>).find { Map option -> option.name == 'package' } == [name: 'package', value: 'com.example', status: 'recorded']
    }

    void "project show says that the package is the default when it is the one a name without a package is given"() {
        given:
        assert ForgeCli.execute(beanContext, 'create-app', 'shop', '--inplace') == 0
        reset()

        when:
        int exit = ForgeCli.execute(beanContext, 'project', 'show')
        List<String> lines = out.toString().readLines()

        then:
        exit == 0
        lines.any { String line -> line ==~ / +package +shop +\(default\)/ }

        when:
        reset()
        exit = ForgeCli.execute(beanContext, 'project', 'show', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map

        then:
        exit == 0
        (document.effectiveOptions as List<Map>).find { Map option -> option.name == 'package' } == [name: 'package', value: 'shop', default: 'shop', status: 'default']
    }

    void "the lines a feature would have added to a file the application does not have are counted and shown without the empty ones"() {
        given: 'an application the shell created, which has no README'
        EarlierApplication.copy(EarlierApplication.GRAILS_8_SHELL, dir)
        EarlierApplication.upgrade(dir)
        assert ForgeCli.execute(beanContext, 'project', 'migrate') == 0
        assert !new File(dir, 'README.md').exists()
        reset()

        when:
        int exit = ForgeCli.execute(beanContext, 'feature', 'add', 'cache', '--dry-run')
        List<String> lines = out.toString().readLines()
        int at = lines.findIndexOf { String line -> line.contains('the feature would have added ') }
        int kept = lines.findIndexOf { String line -> line.contains('the file as this version generates it with the feature is kept as') }
        List<String> shown = lines.subList(at + 1, kept)

        then: 'as many lines as are said, each with something in it'
        exit == 0
        at > 0 && kept > at
        lines[at].trim() == "the feature would have added ${shown.size()} lines to it:".toString()
        shown.size() > 1
        shown.every { String line -> !line.trim().isEmpty() }
        shown.any { String line -> line.contains('## Feature cache documentation') }

        when:
        reset()
        exit = ForgeCli.execute(beanContext, 'feature', 'add', 'cache', '--dry-run', '--json')
        Map document = new JsonSlurper().parseText(out.toString()) as Map
        List<String> details = ((document.files as List<Map>).find { Map file -> file.path == 'README.md' }).details as List<String>

        then: 'and the same in the document'
        exit == 0
        details[0] == "the feature would have added ${shown.size()} lines to it:".toString()
        details.subList(1, details.size() - 1) == shown*.trim().collect { String line -> '    ' + line }
        details.subList(1, details.size() - 1).every { String line -> !line.trim().isEmpty() }
    }
}
