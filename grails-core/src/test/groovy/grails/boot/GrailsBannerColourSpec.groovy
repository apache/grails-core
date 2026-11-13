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
package grails.boot

import org.springframework.boot.ansi.Ansi8BitColor
import org.springframework.boot.ansi.AnsiColor
import org.springframework.boot.ansi.AnsiOutput
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification

/**
 * Covers the banner art being shown in the framework's colour.
 *
 * <p>The escapes are written only where colour has been enabled, and the width the versions beneath
 * are centred on is measured before they are added -- otherwise they would count towards it and
 * push the versions off centre by as many characters as the colour cost.</p>
 */
class GrailsBannerColourSpec extends Specification {

    GrailsBanner banner = new GrailsBanner()

    StandardEnvironment environment = new StandardEnvironment()

    void cleanup() {
        AnsiOutput.setEnabled(AnsiOutput.Enabled.DETECT)
    }

    private void configured(String colour) {
        environment.propertySources.addFirst(
                new MapPropertySource('test', ['grails.banner.art.color': colour]))
    }

    private String printed() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        banner.printBanner(environment, GrailsBannerColourSpec, new PrintStream(bytes))
        bytes.toString()
    }

    void 'the art is the framework amber where nothing is configured'() {
        given:
            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS)

        expect:
            banner.colour('art', environment)
                    .startsWith(AnsiOutput.encode(Ansi8BitColor.foreground(214)))
    }

    void 'the art is left as it stands where colour is off'() {
        given:
            AnsiOutput.setEnabled(AnsiOutput.Enabled.NEVER)

        expect: 'a redirected log, or an application that has turned colour off'
            banner.colour('art', environment) == 'art'
    }

    void 'an application chooses one of the 256 colours by number'() {
        given:
            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS)
            configured('45')

        expect:
            banner.colour('art', environment)
                    .startsWith(AnsiOutput.encode(Ansi8BitColor.foreground(45)))
    }

    void 'an application chooses one of the eight by name'() {
        given:
            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS)
            configured('bright_blue')

        expect: 'read without regard to case, as configuration is written either way'
            banner.colour('art', environment)
                    .startsWith(AnsiOutput.encode(AnsiColor.BRIGHT_BLUE))
    }

    void 'an application asks for no colour at all'() {
        given:
            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS)
            configured('none')

        expect: 'colour everywhere else, and a banner left as it stands'
            banner.colour('art', environment) == 'art'
    }

    void 'a colour that means nothing falls back rather than failing to start'() {
        given:
            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS)
            configured('chartreuse')

        expect:
            banner.colour('art', environment)
                    .startsWith(AnsiOutput.encode(Ansi8BitColor.foreground(214)))
    }

    void 'a number outside the 256 a terminal has falls back'() {
        given:
            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS)

        expect: 'anything else writes an escape the terminal does not understand, which it then shows'
            banner.colour('art', configuredWith('999'))
                    .startsWith(AnsiOutput.encode(Ansi8BitColor.foreground(214)))
            banner.colour('art', configuredWith('-1'))
                    .startsWith(AnsiOutput.encode(Ansi8BitColor.foreground(214)))
    }

    /** A fresh environment each time, so the two values above do not fight over one property source. */
    private StandardEnvironment configuredWith(String colour) {
        StandardEnvironment fresh = new StandardEnvironment()
        fresh.propertySources.addFirst(new MapPropertySource('test', ['grails.banner.art.color': colour]))
        fresh
    }

    void 'the banner carries the colour through to what is printed'() {
        given:
            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS)

        expect:
            printed().contains(AnsiOutput.encode(Ansi8BitColor.foreground(214)))
    }

    void 'the versions stay where they were before the art was coloured'() {
        given:
            AnsiOutput.setEnabled(AnsiOutput.Enabled.NEVER)
            List<String> plain = versionLines(printed())

        when:
            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS)
            List<String> coloured = versionLines(printed().replaceAll(/\[[0-9;]*m/, ''))

        then: 'the width is measured before the escapes are added, so they do not count towards it'
            coloured == plain
            !plain.isEmpty()
    }

    /** The centred lines beneath the art, which is where a mismeasured width would show. */
    private List<String> versionLines(String output) {
        output.readLines().findAll { it.contains('Grails:') || it.contains('Spring') }
    }
}
