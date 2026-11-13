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
import org.springframework.boot.ansi.AnsiOutput
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification

/**
 * Covers the banner saying how an application was started, under the art and above the versions.
 *
 * <p>Written plainly. A banner is printed on the thread that is starting the application, so
 * anything drawn over time here is time the application is not starting.</p>
 */
class GrailsBannerNativeMarkSpec extends Specification {

    /** A banner told what it was started as, so the marks can be covered without being one. */
    static class StartedAs extends GrailsBanner {

        boolean image
        boolean cache

        @Override
        protected boolean isNativeImage() {
            image
        }

        @Override
        protected boolean readsAotCache() {
            cache
        }
    }

    StandardEnvironment environment = new StandardEnvironment()

    void cleanup() {
        AnsiOutput.setEnabled(AnsiOutput.Enabled.DETECT)
        System.clearProperty('spring.aot.enabled')
    }

    private void configured(Map<String, Object> properties) {
        environment.propertySources.addFirst(new MapPropertySource('test', properties))
    }

    private String printed(GrailsBanner banner) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream()
        banner.printBanner(environment, GrailsBannerNativeMarkSpec, new PrintStream(bytes))
        bytes.toString()
    }

    private String plain(GrailsBanner banner) {
        AnsiOutput.setEnabled(AnsiOutput.Enabled.NEVER)
        printed(banner)
    }

    void 'an ordinary start says nothing'() {
        expect: 'no image, no cache, and bean definitions worked out as it went'
            String output = plain(new StartedAs())
            !output.contains('N A T I V E')
            !output.contains('A O T')
    }

    void 'an image says so'() {
        expect:
            plain(new StartedAs(image: true)).contains('N A T I V E')
    }

    void 'a run given a cache to read says so'() {
        expect:
            plain(new StartedAs(cache: true)).contains('A O T   C A C H E')
    }

    void 'a run on generated bean definitions says so'() {
        given: 'the smaller half of the same idea, and a thing an application can be run without'
            System.setProperty('spring.aot.enabled', 'true')

        expect:
            plain(new StartedAs()).contains('A O T')
    }

    void 'an image outranks a cache, which outranks generated definitions'() {
        given:
            System.setProperty('spring.aot.enabled', 'true')

        expect: 'an image has already done all of it, so it is the only thing worth saying'
            plain(new StartedAs(image: true, cache: true)).contains('N A T I V E')
    }

    void 'the mark sits under the art and above the versions'() {
        when:
            String output = plain(new StartedAs(image: true))

        then:
            output.indexOf('N A T I V E') > output.indexOf('grails.apache.org')
            output.indexOf('N A T I V E') < output.indexOf('JVM')
    }

    void 'the mark is centred on the art'() {
        when:
            List<String> lines = plain(new StartedAs(image: true)).readLines()
            String mark = lines.find { it.contains('N A T I V E') }
            int artWidth = lines*.length().max()

        then: 'the same space either side, give or take the odd character'
            int leading = mark.length() - mark.stripLeading().length()
            int trailing = artWidth - mark.length()
            Math.abs(leading - trailing) <= 1
    }

    void 'nothing is drawn over, wherever it is read'() {
        when: 'a terminal, a redirected log, or an application that has turned colour off'
            String output = plain(new StartedAs(image: true))

        then: 'one word, once'
            output.count('N A T I V E') == 1

        and: 'nothing drawn over -- a return only ever ends a line, never rewinds one'
            // A banner is printed with println, so the line ending is the platform's: \n where
            // this is read on Linux or macOS, \r\n on Windows. What over-drawing looks like is a
            // return on its own, so that is what this asks about, on every platform alike.
            !output.replace('\r\n', '\n').contains('\r')
    }

    void 'an application can say something else'() {
        expect:
            configured(['grails.banner.mark.text': 'Leyden'])
            plain(new StartedAs(cache: true)).contains('L e y d e n')
    }

    void 'a mark is spaced as it was written rather than shouted'() {
        expect: 'a name whose case is part of it keeps it -- CRaC is not CRAC'
            configured(['grails.banner.mark.text': 'CRaC'])
            plain(new StartedAs()).contains('C R a C')
    }

    void 'the marks it works out for itself are still upper case'() {
        expect: 'they are written that way, rather than made that way'
            plain(new StartedAs(image: true)).contains('N A T I V E')
            plain(new StartedAs(cache: true)).contains('A O T   C A C H E')
    }

    void 'the mark is a different colour from the art'() {
        given:
            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS)

        when: 'the art amber and the mark bright yellow, so one is not read as part of the other'
            String output = printed(new StartedAs(image: true))
            String markLine = output.readLines().find { it.contains('N A T I V E') }

        then:
            markLine.contains(AnsiOutput.encode(Ansi8BitColor.foreground(226)))
            !markLine.contains(AnsiOutput.encode(Ansi8BitColor.foreground(214)))
    }

    void 'an application can choose the colour the mark is shown in'() {
        given:
            AnsiOutput.setEnabled(AnsiOutput.Enabled.ALWAYS)
            configured(['grails.banner.mark.color': '39'])

        expect:
            printed(new StartedAs(image: true)).readLines()
                    .find { it.contains('N A T I V E') }
                    .contains(AnsiOutput.encode(Ansi8BitColor.foreground(39)))
    }

    void 'spring security is a default rather than something to ask for'() {
        expect: 'so an application with it on the classpath shows it without configuring anything'
            GrailsBanner.DefaultVersionOption.values()*.key.contains('spring-security')
            !GrailsBanner.OptionalVersionOption.values()*.key.contains('spring-security')
    }

    void 'the container served on is a default, and naming a particular one stays optional'() {
        expect: 'every application has a container, so it is shown without being asked for'
            GrailsBanner.DefaultVersionOption.values()*.key.contains('container')

        and: 'and the specific ones remain, for an application that wants one whatever is serving'
            GrailsBanner.OptionalVersionOption.values()*.key == ['tomcat', 'jetty', 'undertow']
    }

    void 'a version asked for by name that cannot be determined is shown as unknown'() {
        expect: 'jetty and undertow are not on this classpath, and saying nothing about a version ' +
                'that was asked for by name reads as the option having been ignored'
            configured(['grails.banner.versions.include': 'jetty,undertow'])
            String output = plain(new StartedAs())
            output.contains('Jetty: unknown')
            output.contains('Undertow: unknown')
    }

    void 'a version shown by default that cannot be determined is left out'() {
        expect: 'which is what lets one be shown by default at all -- an application without Spring ' +
                'Security says nothing about it rather than saying it does not know'
            configured([:])
            !plain(new StartedAs()).contains('Spring Security')
    }

    void 'an application can turn spring security off'() {
        expect: 'it is a default now, so exclude is what removes it'
            configured(['grails.banner.versions.exclude': 'spring-security'])
            !plain(new StartedAs()).contains('Spring Security')
    }

    void 'an application can turn the mark off altogether'() {
        expect:
            configured(['grails.banner.mark.display': false])
            !plain(new StartedAs(image: true)).contains('N A T I V E')
    }
}
