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

import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import spock.lang.Specification

/**
 * Covers reading an optional library's version without setting the library going.
 *
 * <p>A version is read <em>about</em> a library, not <em>from</em> it. Initialising the class to
 * find one lets it do whatever it does on the way: Spring Security logs
 * {@code You are running with Spring Security Core ...} from its static initialiser, and that line
 * arrived in the middle of the banner -- between the mark and the very versions it was being read
 * for -- because the lookup had initialised it.</p>
 */
class GrailsBannerVersionLookupSpec extends Specification {

    /**
     * Where the record is kept, so that reading it is not itself what sets {@link Noisy} going.
     * Asking the class under test whether it was initialised initialises it.
     */
    static class Record {

        static boolean noisyRan = false
    }

    /** Stands in for a library that does something when it starts. */
    static class Noisy {

        static {
            Record.noisyRan = true
        }
    }

    void 'reading a version does not set the library going'() {
        when:
            GrailsBanner.findVersion(Noisy.name)

        then: 'loaded, but its static initialiser has not run'
            !Record.noisyRan

        and: 'and it really would have run, had the class been initialised'
            Class.forName(Noisy.name, true, getClass().classLoader)
            Record.noisyRan
    }

    void 'a library that is not there has no version rather than an error'() {
        expect: 'nothing, so the banner leaves it out rather than saying it does not know'
            GrailsBanner.findVersion('com.example.NotOnTheClasspath') == null
    }

    void 'a library with no version in its manifest has none'() {
        expect: 'test classes carry no Implementation-Version, and that is not a failure'
            GrailsBanner.findVersion(Noisy.name) == null
    }

    void 'a version recorded in a manifest is read'() {
        expect: 'Spock ships one, so this proves the lookup reads rather than always saying unknown'
            GrailsBanner.findVersion(Specification.name) ==~ /\d+\..*/
    }

    void 'a version recorded in a resource is read'() {
        expect: 'the route a container version takes, which a manifest cannot take out of a jar'
            GrailsBanner.findVersionInResource('grails/boot/version-in-a-resource.properties',
                    'server.number') == '1.2.3'
    }

    void 'a resource that is not there has no version rather than an error'() {
        expect:
            GrailsBanner.findVersionInResource('grails/boot/no-such-resource.properties',
                    'server.number') == null
    }

    void 'a resource that records no such version has none'() {
        expect: 'a library may ship the file and still not answer this question'
            GrailsBanner.findVersionInResource('grails/boot/version-in-a-resource.properties',
                    'server.unrecorded') == null
    }

    /** A banner told which containers are there, and remembering which it was asked about. */
    static class Serving extends GrailsBanner {

        Map<String, String> present = [:]

        List<String> asked = []

        @Override
        protected String findTomcatVersion() {
            asked << 'tomcat'
            present['tomcat']
        }

        @Override
        protected String findJettyVersion() {
            asked << 'jetty'
            present['jetty']
        }

        @Override
        protected String findUndertowVersion() {
            asked << 'undertow'
            present['undertow']
        }
    }

    private static StandardEnvironment configured(Map<String, Object> properties = [:]) {
        StandardEnvironment environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', properties))
        environment
    }

    void 'the container being served on is shown, named as itself'() {
        expect:
            new Serving(present: ['tomcat': '1.1.1']).findContainerVersion() == ['Tomcat': '1.1.1']
    }

    void 'the ones it cannot also be running on are not looked for'() {
        given: 'an application serves on one container, so the second is not a question worth asking'
            Serving banner = new Serving(present: ['tomcat': '1.1.1', 'jetty': '2.2.2'])

        when:
            Map<String, String> container = banner.findContainerVersion()

        then: 'the most commonly used of the two, and only that one shown'
            container == ['Tomcat': '1.1.1']

        and: 'and only that one asked about'
            banner.asked == ['tomcat']
    }

    void 'the next one is tried when the one before it is not there'() {
        expect:
            new Serving(present: ['jetty': '2.2.2']).findContainerVersion() == ['Jetty': '2.2.2']
            new Serving(present: ['undertow': '3.3.3']).findContainerVersion() == ['Undertow': '3.3.3']
    }

    void 'an application on none of them says nothing rather than that it does not know'() {
        when:
            Serving banner = new Serving()

        then:
            banner.findContainerVersion().isEmpty()

        and: 'having asked about each, since any of them could have been the one'
            banner.asked == ['tomcat', 'jetty', 'undertow']
    }

    void 'the container is shown without an application asking for it'() {
        expect:
            new Serving(present: ['tomcat': '1.1.1']).createBannerVersions(configured())['Tomcat'] == '1.1.1'
    }

    void 'an application can leave the container out'() {
        expect: 'one key covers whichever container it is, so this is how it is turned off'
            !new Serving(present: ['tomcat': '1.1.1'])
                    .createBannerVersions(configured(['grails.banner.versions.exclude': 'container']))
                    .containsKey('Tomcat')
    }

    void 'naming the container as well as being shown it by default shows it once'() {
        when: 'an application that asked for tomcat before it was shown by default keeps working'
            Serving banner = new Serving(present: ['tomcat': '1.1.1'])
            ByteArrayOutputStream bytes = new ByteArrayOutputStream()
            banner.printBanner(configured(['grails.banner.versions.include': 'tomcat']),
                    GrailsBannerVersionLookupSpec, new PrintStream(bytes))

        then:
            bytes.toString().count('Tomcat') == 1
    }

    void 'an option that has moved between the lists is still recognised'() {
        expect: 'spring-security was asked for before it was shown by default, and an application ' +
                'that still asks for it named something this understands'
            new Serving().unrecognisedVersionOptions(
                    configured(['grails.banner.versions.include': 'spring-security'])).isEmpty()
    }

    void 'an option shown by default can be named in either list'() {
        expect: 'which list an option belongs to is this class\'s decision to change, so naming one ' +
                'is not a mistake an application made'
            new Serving().unrecognisedVersionOptions(configured([
                    'grails.banner.versions.include': 'container',
                    'grails.banner.versions.exclude': 'tomcat'])).isEmpty()
    }

    void 'an option that names nothing is reported'() {
        expect: 'dropped silently, a typo reads as a banner ignoring what it was told, and the only ' +
                'way to find out is to notice a line that is not there'
            new Serving().unrecognisedVersionOptions(
                    configured(['grails.banner.versions.include': 'spring-securty'])) == ['spring-securty']
    }

    void 'every place an option can be written is checked'() {
        expect:
            new Serving().unrecognisedVersionOptions(configured([
                    'grails.banner.versions.order': 'grails,ordr',
                    'grails.banner.versions.exclude': 'excloode',
                    'grails.banner.versions.include': 'includ'])) == ['ordr', 'excloode', 'includ']
    }

    void 'an application that named them all correctly is told nothing'() {
        expect:
            new Serving().unrecognisedVersionOptions(configured([
                    'grails.banner.versions.order': 'grails,groovy',
                    'grails.banner.versions.exclude': 'app',
                    'grails.banner.versions.include': 'jetty'])).isEmpty()
    }
}
