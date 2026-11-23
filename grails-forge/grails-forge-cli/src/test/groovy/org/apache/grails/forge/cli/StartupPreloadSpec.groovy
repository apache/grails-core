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
package org.apache.grails.forge.cli

import java.util.concurrent.CopyOnWriteArrayList
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The classes the command line loads ahead: loaded and not initialized, on daemon threads, and never
 * in the way of the command, whatever the lists hold.
 */
class StartupPreloadSpec extends Specification {

    static final String MARK = 'grails.preload.spec.initialized'
    static final String MARKED = 'org.apache.grails.forge.cli.StartupPreloadSpec$Marked'
    static final String OF_GENERATOR = 'org.apache.grails.forge.cli.StartupPreloadSpec$OfGenerator'

    void cleanup() {
        System.clearProperty(MARK)
        System.clearProperty(StartupPreload.PROPERTY)
    }

    void "the classes of the list are loaded and not initialized, and what is no class is passed over"() {
        given:
        Recording loader = new Recording((StartupPreload.CLASSES): "# a comment\n\n  ${MARKED}  \nno.such.Class\nnot a name at all\n${MARKED}\n")

        when:
        List<Thread> threads = StartupPreload.start(loader, 4, '--version')
        threads*.join(30_000)

        then:
        threads*.name == ['grails-preload-classes', 'grails-preload-formatting']
        threads.every { Thread thread -> thread.daemon && !thread.alive }
        loader.asked.contains(MARKED)
        loader.asked.contains('not a name at all')
        loader.asked.contains('no.such.Class')
        loader.resources == [StartupPreload.CLASSES]

        and: 'the class is there, and its initializer has not run'
        System.getProperty(MARK) == null

        when: 'the command uses it'
        Class.forName(MARKED, true, loader)

        then:
        System.getProperty(MARK) == 'Marked'
    }

    @Unroll
    void "the classes of the generator are loaded for a command that starts it and for no other: #arguments"() {
        given:
        Recording loader = new Recording((StartupPreload.CLASSES): "${MARKED}\n", (StartupPreload.GENERATOR_CLASSES): "${OF_GENERATOR}\n")

        when:
        StartupPreload.start(loader, 2, arguments as String[])*.join(30_000)

        then:
        loader.asked.contains(MARKED)
        loader.asked.contains(OF_GENERATOR) == generator
        System.getProperty(MARK) == null

        where:
        arguments                                   | generator
        ['create-app', 'demo']                      | true
        ['--plain-output', 'create-plugin', 'demo'] | true
        ['create-restapi', 'demo']                  | true
        ['create-controller', 'Book']               | false
        ['help', 'create-app']                      | false
        ['--version']                               | false
        []                                          | false
        ['feature', 'add', 'cache']                 | true
        ['--plain-output', 'feature', 'list']       | true
        ['project', 'regenerate', '--diff']         | true
        ['project', 'migrate', '--dry-run']         | true
        ['feature']                                 | false
        ['feature', '--help']                       | false
        ['project', 'show']                         | false
        ['help', 'feature', 'add']                  | false
        ['add', 'feature']                          | false
        [null, 'feature', null, 'list']             | true
    }

    void "nothing is started where the JVM has one processor, or where the system property says so"() {
        given:
        Recording loader = new Recording((StartupPreload.CLASSES): "${MARKED}\n")

        expect:
        StartupPreload.start(loader, 1, '--version') == []

        when:
        System.setProperty(StartupPreload.PROPERTY, 'false')
        Set<Thread> before = Thread.allStackTraces.keySet()
        StartupPreload.start(loader, '--version')
        Set<Thread> after = Thread.allStackTraces.keySet()

        then:
        !(after - before).any { Thread thread -> thread.name.startsWith('grails-preload') }
        loader.asked.isEmpty()
        loader.resources.isEmpty()
    }

    void "a list that is missing, or cannot be read, or a class loader that fails, is no failure of the command"() {
        when: 'no list at all'
        Recording none = new Recording([:])
        StartupPreload.start(none, 2, '--version')*.join(30_000)

        then:
        noExceptionThrown()
        none.resources == [StartupPreload.CLASSES]

        when: 'a list that fails while it is read, and a class loader that throws for every class'
        Recording failing = new Recording((StartupPreload.CLASSES): "${MARKED}\n")
        failing.fails = true
        StartupPreload.start(failing, 2, '--version')*.join(30_000)
        StartupPreload.start(failing, '--version')

        then:
        noExceptionThrown()

        when: 'no class loader'
        List<Thread> threads = StartupPreload.start(null, 2, '--version')
        StartupPreload.start(null, '--version')

        then:
        noExceptionThrown()
        threads == []
    }

    void "the lists of the command line are read by the class loader it is given, and name classes it has"() {
        given:
        Recording loader = new Recording(null)

        when:
        StartupPreload.start(loader, 2, 'create-app', 'demo')*.join(120_000)

        then: 'both lists were read, and classes of the command line and of the generator were asked for'
        loader.resources == [StartupPreload.CLASSES, StartupPreload.GENERATOR_CLASSES]
        loader.asked.contains('org.grails.forge.cli.ForgeCli')
        loader.asked.contains('picocli.CommandLine')
        loader.asked.size() > 1000
    }

    void "what was done is said for the diagnostics of the command line: started and for which lists, and whether they still load, or why nothing was"() {
        given:
        Recording loader = new Recording((StartupPreload.CLASSES): "${MARKED}\n", (StartupPreload.GENERATOR_CLASSES): "${OF_GENERATOR}\n")

        when:
        List<Thread> threads = StartupPreload.start(loader, 2, '--version')
        threads*.join(30_000)

        then:
        StartupPreload.status() == 'started: the classes every command loads, done'

        when:
        StartupPreload.start(loader, 2, 'create-app', 'demo')*.join(30_000)

        then:
        StartupPreload.status() == 'started: the classes every command loads and those of the project generator, done'

        when:
        StartupPreload.start(loader, 1, '--version')

        then:
        StartupPreload.status() == 'off: the JVM has one processor'

        when:
        StartupPreload.start(null, 2, '--version')

        then:
        StartupPreload.status() == 'off: there is no class loader'

        when:
        System.setProperty(StartupPreload.PROPERTY, 'false')
        StartupPreload.start(loader, '--version')

        then:
        StartupPreload.status() == 'off: the system property grails.cli.preload is false'
    }

    // A class loader that writes down what it is asked for. With no lists of its own it hands out those of the class path.
    static class Recording extends ClassLoader {

        final List<String> asked = new CopyOnWriteArrayList<>()
        final List<String> resources = new CopyOnWriteArrayList<>()
        final Map<String, String> lists
        volatile boolean fails

        Recording(Map<String, String> lists) {
            super(StartupPreloadSpec.classLoader)
            this.lists = lists
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            // the Groovy runtime and the class that formats are asked for as well: only the lists are of interest
            if (!name.startsWith('groovy.lang.') && !name.startsWith('org.codehaus.groovy.runtime.FormatHelper')) {
                asked.add(name)
            }
            if (fails) {
                throw new IllegalStateException('this class loader fails')
            }
            return super.loadClass(name, resolve)
        }

        @Override
        InputStream getResourceAsStream(String name) {
            resources.add(name)
            if (fails) {
                return new InputStream() {

                    @Override
                    int read() throws IOException {
                        throw new IOException('this list cannot be read')
                    }
                }
            }
            if (lists == null) {
                return super.getResourceAsStream(name)
            }
            String list = lists.get(name)
            return list == null ? null : new ByteArrayInputStream(list.getBytes('UTF-8'))
        }
    }

    static class Marked {

        static {
            System.setProperty('grails.preload.spec.initialized', 'Marked')
        }
    }

    static class OfGenerator {

        static {
            System.setProperty('grails.preload.spec.initialized', 'OfGenerator')
        }
    }
}
