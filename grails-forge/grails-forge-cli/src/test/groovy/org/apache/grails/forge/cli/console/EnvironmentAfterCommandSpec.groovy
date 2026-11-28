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
package org.apache.grails.forge.cli.console

import java.nio.charset.StandardCharsets
import org.grails.forge.cli.CommandSpec
import org.grails.forge.cli.ForgeCli
import spock.lang.IgnoreIf
import spock.lang.Unroll

/**
 * What {@code -Dgrails.env=NAME} after the name of a command does, for each kind of command: a command
 * of {@code grails} itself that runs the build runs in that environment; a command of the application
 * or of a plugin, run by its name, is given it as an argument, runs in {@code development}, and a
 * warning says so; {@code run-tool}, and its deprecated name {@code run-command}, takes the environment in front of the
 * name of the tool, and after it gives it to the tool without a word; a script is given it as an argument and runs in the
 * environment of the build. The wrapper of the project here writes down what Gradle would be given.
 */
@IgnoreIf({ os.windows })
class EnvironmentAfterCommandSpec extends CommandSpec {

    static final String WARNING = '| Warning -Dgrails.env=... after the name of '

    PrintStream originalOut
    PrintStream originalErr
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    ByteArrayOutputStream err = new ByteArrayOutputStream()
    File given

    void setup() {
        originalOut = System.out
        originalErr = System.err
        System.setOut(new PrintStream(out, true))
        System.setErr(new PrintStream(err, true))
        new File(dir, 'grails-app').mkdirs()
        new File(dir, 'src/main/scripts').mkdirs()
        new File(dir, 'src/main/scripts/data-import.groovy').text = 'println args\n'
        File catalog = new File(dir, ToolCatalogFile.LOCATION)
        catalog.parentFile.mkdirs()
        catalog.setText(ProjectToolsSpec.doc([
                [name: 'audit-report', aliases: [], description: 'Writes the audit report', origin: 'application', target: 'application', registration: 'factories'],
                [name: 'hello-world', aliases: [], description: 'Says hello', origin: 'org.example:hello-cli:1.2.3', target: 'application', registration: 'descriptor']
        ]), StandardCharsets.UTF_8.name())
        given = new File(dir, 'wrapper-given')
        File wrapper = new File(dir, 'gradlew')
        wrapper.text = '#!/bin/sh\nprintf \'%s\\n\' "$@" > "$(dirname "$0")/wrapper-given"\n'
        wrapper.setExecutable(true, false)
    }

    void cleanup() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }

    @Unroll
    void "#typed: the build is run for #environment, and #what"() {
        when:
        int exit = ForgeCli.execute(beanContext, typed.split(' '))
        List<String> gradle = given.readLines()

        then:
        exit == 0
        gradle.findAll { it.startsWith('-Dgrails.env=') } == (environment == 'the build' ? [] : ["-Dgrails.env=${environment}".toString()])
        err.toString().contains(WARNING) == warned
        !warned || err.toString().readLines().contains(WARNING + typed.split(' ')[0] + ' is given to the tool as an argument, and the tool runs for the environment ' +
                "development. To run it for another environment, name it before the tool: grails-forge-cli run-tool -Dgrails.env=production ${typed.split(' ')[0]}")

        where:
        typed                                                  | environment   | warned
        'compile -Dgrails.env=production'                      | 'production'  | false
        'audit-report -Dgrails.env=production'                 | 'development' | true
        'hello-world -Dgrails.env=production'                  | 'development' | true
        'run-tool -Dgrails.env=production hello-world'         | 'production'  | false
        'run-tool hello-world -Dgrails.env=production'         | 'development' | false
        'run-command -Dgrails.env=production hello-world'      | 'production'  | false
        'data-import -Dgrails.env=production'                  | 'the build'   | false
        what = warned ? 'a warning says that the option is an argument of the tool' : 'nothing is said of the option'
    }
}
