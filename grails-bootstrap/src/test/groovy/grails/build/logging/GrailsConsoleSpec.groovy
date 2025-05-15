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
package grails.build.logging

import jline.console.ConsoleReader
import org.fusesource.jansi.Ansi
import spock.lang.IgnoreIf
import spock.lang.Issue
import spock.lang.PendingFeatureIf
import spock.lang.Specification

import java.util.regex.Pattern

/**
 * Tests of the GrailsConsole.
 *
 * Only the following methods output ansi sequences or color codes directly:
 * - outputMessage
 * - error
 * - userInput
 *
 * The goal of the test is to verify that the RESET sequence is written to the output after
 * the invocation of the aforementioned methods
 *
 * @author Tom Bujok
 * @since 2.3
 */
@IgnoreIf({
    !GrailsConsole.instance.isAnsiEnabled() ||
    GroovySystem.version.startsWith('5') // Mock() does currently not work with Groovy 5
})
class GrailsConsoleSpec extends Specification {

    static final String RESET = Pattern.quote(Ansi.ansi().reset().toString())

    PrintStream out
    GrailsConsole console
    String output

    def setup() {
        InputStream systemIn = Mock(InputStream)
        systemIn.read(* _) >> -1
        out = Mock(PrintStream)

        console = GrailsConsole.getInstance()
        console.ansiEnabled = true
        console.out = out
        console.reader = new ConsoleReader(systemIn, out)

        output = ""
    }

    @Issue('GRAILS-10753')
    def "outputMessage - verify the reset marker at the end of the output"() {
        when:
        console.outputMessage("MSG", 1)

        then:
        out./print.*/(* _) >> { def args -> output += args.join('') }
        assert assertResetMarkAtTheEndOfOutput(output)
    }

    @Issue('GRAILS-10753')
    def "error - verify the reset marker at the end of the output"() {
        when:
        console.error("LABEL", "MSG")

        then:
        out./print.*/(* _) >> { def args -> output += args.join('') }
        assert assertResetMarkAtTheEndOfOutput(output)
    }

    @Issue('GRAILS-10753')
    def "userInput - verify the reset marker at the end of the output"() {
        when:
        console.userInput("QUESTION")

        then:
        out./write.*/(* _) >> { def args -> output = new String(args[0], args[1], args[2]) }
        assert assertResetMarkAtTheEndOfOutput(output)
    }

    def assertResetMarkAtTheEndOfOutput(def output) {
        return output ==~ /.*$RESET/
    }

}
