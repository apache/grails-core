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

import org.jline.reader.LineReaderBuilder
import org.jline.terminal.Terminal
import org.jline.terminal.impl.ExternalTerminal
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Tests of the GrailsConsole ansi rendering.
 *
 * These assertions describe the escape sequences the console actually emits, so the rendering can be
 * re-implemented (for example on a different ansi library) without silently changing what reaches a
 * user's terminal.
 *
 * The console is driven through a terminal built over plain streams rather than the ambient one.
 * A Gradle test worker has no tty, so the ambient terminal is always {@code dumb} and
 * {@code isAnsiEnabled()} is false - this spec used to be gated on that and therefore never ran
 * anywhere, locally or in CI.
 *
 * @author Tom Bujok
 * @since 2.3
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class GrailsConsoleSpec extends Specification {

    /**
     * The escape character that introduces every sequence asserted below, written as a unicode escape
     * so it survives tooling that would mangle a raw control byte in source.
     *
     * <p>Every pattern here includes it deliberately: matching on a bare {@code [} would keep passing
     * if the CSI introducer were dropped from {@code ConsoleAnsi} entirely - a regression that puts
     * literal {@code [1;33m} text on a user's terminal, which is what these tests exist to catch.</p>
     */
    static final String ESC = '\u001B'

    /** Accepts either form of the SGR reset sequence: ESC[m and ESC[0m are both a full reset. */
    static final Pattern RESET_AT_END = Pattern.compile("(?s).*${ESC}\\[0?m\$")

    /** Matches every SGR escape (ESC[<params>m) so codes can be checked regardless of grouping. */
    static final Pattern SGR = Pattern.compile("${ESC}\\[([0-9;]*)m")

    PrintStream out
    GrailsConsole console
    String output
    Terminal terminal
    ByteArrayOutputStream terminalOut

    def setup() {
        out = Mock(PrintStream)
        terminalOut = new ByteArrayOutputStream()
        // ExternalTerminal rather than TerminalBuilder: asking the builder for a non-dumb terminal
        // over streams makes it allocate a real PTY, and closing that PTY's file descriptor blocks
        // indefinitely in a test JVM. ExternalTerminal gives the same non-dumb type with no native
        // handle. The input stream must supply lines because userInput reads for real here.
        terminal = new ExternalTerminal('grails-console-spec', 'xterm-256color',
                new ByteArrayInputStream(('\n' * 10).bytes), terminalOut,
                StandardCharsets.UTF_8)

        console = GrailsConsole.getInstance()
        console.ansiEnabled = true
        console.out = out
        console.@terminal = terminal
        console.@reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .build()

        output = ""
    }

    def cleanup() {
        // the console is a JVM-wide singleton and this spec mutates its streams and terminal;
        // drop it so tests sharing the fork get a clean instance
        GrailsConsole.removeInstance()
        terminal?.close()
    }

    /**
     * Collects every SGR code emitted, so a test can assert on intent (bold, red) without pinning the
     * grouping - jansi writes {@code ESC[1;31m} where other renderers write {@code ESC[1m ESC[31m}.
     */
    private static Set<Integer> sgrCodes(String text) {
        Set<Integer> codes = [] as Set
        def matcher = SGR.matcher(text)
        while (matcher.find()) {
            String params = matcher.group(1)
            if (!params) {
                codes << 0
                continue
            }
            params.split(';').each { codes << (it ? it as Integer : 0) }
        }
        codes
    }

    def "the console under test has ansi enabled"() {
        expect: 'the explicitly typed terminal keeps the ansi code paths live'
        console.isAnsiEnabled()
    }

    @Issue('GRAILS-10753')
    def "outputMessage - verify the reset marker at the end of the output"() {
        when:
        console.outputMessage("MSG", 1)

        then:
        out./print.*/(* _) >> { def args -> output += args.join('') }
        output ==~ RESET_AT_END
    }

    @Issue('GRAILS-10753')
    def "error - verify the reset marker at the end of the output"() {
        when:
        console.error("LABEL", "MSG")

        then:
        out./print.*/(* _) >> { def args -> output += args.join('') }
        output ==~ RESET_AT_END
    }

    def "error renders the label bold red and restores bold-off and the default foreground"() {
        when:
        console.error("LABEL", "MSG")

        then:
        out./print.*/(* _) >> { def args -> output += args.join('') }

        and: 'bold (1) and red (31), then bold-off (22) and default foreground (39)'
        sgrCodes(output).containsAll([1, 31, 22, 39])

        and: 'the text survives the escape sequences'
        output.contains('LABEL')
        output.contains('MSG')
    }

    def "outputMessage renders the category separator bold yellow"() {
        when:
        console.outputMessage("MSG", 1)

        then:
        out./print.*/(* _) >> { def args -> output += args.join('') }

        and: 'bold (1) and yellow (33), then bold-off (22) and default foreground (39)'
        sgrCodes(output).containsAll([1, 33, 22, 39])

        and:
        output.contains('MSG')
    }

    def "outputMessage rewinds over the previous line using cursor movement and erase"() {
        when:
        console.outputMessage("MSG", 1)

        then:
        out./print.*/(* _) >> { def args -> output += args.join('') }

        and: 'cursor up, cursor left and erase-to-end-of-line'
        output.contains("${ESC}[1A")
        output =~ Pattern.compile("${ESC}\\[\\d+D")
        output.contains("${ESC}[0K")
    }

    def "userInput writes the prompt through the terminal"() {
        when:
        console.userInput("QUESTION")

        then:
        terminalOut.toString(StandardCharsets.UTF_8).contains('QUESTION')
    }

    @Issue('GRAILS-10753')
    def "userInput - the styled prompt closes its own styling"() {
        when:
        console.userInput("QUESTION")

        then: 'the prompt must not leave the terminal stuck in bold/yellow after the question'
        String written = terminalOut.toString(StandardCharsets.UTF_8)
        written.contains('QUESTION')
        SGR.matcher(written).find()
        sgrCodes(written).contains(0)
    }

    def "Spring Boot's spring.output.ansi.enabled=never suppresses ansi output"() {
        given: 'a terminal that would otherwise support ansi'
        assert console.isAnsiEnabled()

        when:
        System.setProperty('spring.output.ansi.enabled', 'never')

        then:
        !console.isAnsiEnabled()

        cleanup:
        System.clearProperty('spring.output.ansi.enabled')
    }

    def "spring.output.ansi.enabled=always keeps ansi on for a dumb terminal"() {
        given:
        console.@terminal = new ExternalTerminal('dumb-spec', 'dumb',
                new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream(), StandardCharsets.UTF_8)
        assert !console.isAnsiEnabled()

        when:
        System.setProperty('spring.output.ansi.enabled', 'always')

        then:
        console.isAnsiEnabled()

        cleanup:
        System.clearProperty('spring.output.ansi.enabled')
    }

    def "log does not erase the prompt with escapes when ansi output is disabled"() {
        given: 'a prompt is on screen and ansi is off'
        console.ansiEnabled = false
        console.@userInputActive = true

        when:
        console.log('MSG')

        then:
        out./print.*/(* _) >> { def args -> output += args.join('') }

        and: 'the prompt erase must not leak raw escape bytes into piped output'
        output.contains('MSG')
        !output.contains(ESC)
    }

    def "append does not move the cursor with escapes when ansi output is disabled"() {
        given:
        console.ansiEnabled = false
        console.@userInputActive = true

        when:
        console.append('MSG')

        then:
        out./print.*/(* _) >> { def args -> output += args.join('') }

        and:
        output.contains('MSG')
        !output.contains(ESC)
    }

    def "an unusable spring.output.ansi.enabled value is reported rather than silently ignored"() {
        given:
        def captured = new ByteArrayOutputStream()
        console.@err = new PrintStream(captured, true)

        when: 'a plausible but unsupported value - the enum only accepts always/detect/never'
        System.setProperty('spring.output.ansi.enabled', 'false')

        then: 'it falls back to detect, but says so instead of silently reading as colour-on'
        console.isAnsiEnabled()
        captured.toString(StandardCharsets.UTF_8).contains('spring.output.ansi.enabled')

        cleanup:
        System.clearProperty('spring.output.ansi.enabled')
    }

    def "no ansi sequences are emitted when ansi output is disabled"() {
        given:
        console.ansiEnabled = false

        when:
        console.outputMessage("MSG", 1)

        then:
        out./print.*/(* _) >> { def args -> output += args.join('') }

        and: 'plain text only - no escape sequences at all'
        output.contains('MSG')
        !output.contains(ESC)
    }
}
