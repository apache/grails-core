/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file to You under
 *  the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.ui.shell.support

import java.nio.file.Path

import org.apache.groovy.groovysh.GroovyshOptions
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import spock.lang.Specification
import spock.lang.TempDir
import spock.util.environment.RestoreSystemProperties

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication

/**
 * Starts the Groovy shell {@code grails shell} opens, on the class path the console module ships.
 */
@RestoreSystemProperties
class GroovyshApplicationContextSpec extends Specification {

    @TempDir
    Path userHome

    void setup() {
        // groovysh keeps its history and settings under the user's home directory
        System.setProperty('user.home', userHome.toString())
    }

    void 'the shell starts with the application in its binding and ends when its input ends'() {
        given: 'a terminal whose input is empty, so the shell never waits for the console of an IDE'
        ByteArrayOutputStream output = new ByteArrayOutputStream()
        Terminal terminal = TerminalBuilder.builder().system(false).dumb(true)
                .streams(new ByteArrayInputStream(new byte[0]), output).build()

        and: 'an application context with a Grails application, as grails shell starts it'
        def context = new GroovyshApplicationContext() {
            @Override
            protected GroovyshOptions.Builder shellOptions() {
                super.shellOptions().terminal(terminal)
            }
        }
        context.registerBean(GrailsApplication.APPLICATION_ID, GrailsApplication, { new DefaultGrailsApplication() })

        when: 'the context refreshes, which starts the shell'
        context.refresh()

        then: 'the shell runs on that terminal and returns without a JLine class missing from the class path'
        noExceptionThrown()
        context.active
        output.toString('UTF-8').contains('groovy> ')

        cleanup:
        context.close()
        terminal.close()
    }
}
