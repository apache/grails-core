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
package org.grails.datastore.gorm.mongodb.embedded

import com.mongodb.client.MongoClient
import com.mongodb.client.MongoClients

import org.bson.Document

import org.springframework.context.aot.AbstractAotProcessor
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Path

/**
 * Exercises both backends against real servers. Servers started here are reaped by the
 * shutdown hook the initializer registers, when this JVM exits.
 */
class EmbeddedMongoInitializerSpec extends Specification {

    @TempDir
    Path temp


    void 'a url naming a host is left to the driver'() {
        given: 'what a production environment configures'
        GenericApplicationContext context = contextWith([
                'grails.mongodb.url': 'mongodb://localhost:27017/bookstore',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then: 'nothing was started and the url was not touched, the way an application with h2 on ' +
                'the classpath and a PostgreSQL url never starts H2'
        context.environment.getProperty('grails.mongodb.url') == 'mongodb://localhost:27017/bookstore'
        context.environment.propertySources.every { it.name != 'embeddedMongoDB' }
    }

    void 'nothing is started when no url is configured at all'() {
        given:
        GenericApplicationContext context = contextWith([:])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then:
        !context.environment.getProperty(EmbeddedMongoInitializer.DEFAULT_PROPERTY_NAME)
        context.environment.propertySources.every { it.name != 'embeddedMongoDB' }
    }

    void 'the in-memory backend serves a real MongoDB connection'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:27981/bookstore',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)
        String url = context.environment.getProperty('grails.mongodb.url')

        then: 'the database name came from the url that asked for the server'
        url == 'mongodb://localhost:27981/bookstore'

        and: 'a driver can round-trip a document through it'
        roundTrip(url) == 'in-memory'
    }

    void 'the flapdoodle backend serves a real MongoDB connection'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): FlapdoodleMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:27982/bookstore',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)
        String url = context.environment.getProperty('grails.mongodb.url')

        then:
        url == 'mongodb://localhost:27982/bookstore'
        roundTrip(url) == 'flapdoodle'
    }

    void 'flapdoodle is preferred when both backends are on the classpath'() {
        given: 'no backend is named, and this module has both available in tests'
        GenericApplicationContext context = contextWith([
                'grails.mongodb.url': 'mongodb://embedded:27983',
        ])

        when: 'adding flapdoodle is the opt-in for a real mongod'
        new EmbeddedMongoInitializer().initialize(context)

        then: 'a real mongod answers, which the in-memory backend could not do for a transaction'
        context.environment.getProperty('grails.mongodb.url') == 'mongodb://localhost:27983/test'
    }

    void 'a url that names no port is offset by however far the server port has moved'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'server.port'                     : '9050',
                'grails.mongodb.url'              : 'mongodb://embedded/bookstore',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then: 'so two applications that did not name a port do not collide'
        context.environment.getProperty('grails.mongodb.url') == 'mongodb://localhost:27987/bookstore'
    }

    void 'credentials copied from a real url do not stop it being recognised'() {
        given: 'there is nothing to authenticate against, but the url still asks for embedded'
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://user:secret@embedded:28002/bookstore',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then:
        context.environment.getProperty('grails.mongodb.url') == 'mongodb://localhost:28002/bookstore'
    }

    void 'the url is published into every configured property'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND)       : InMemoryMongoBackend.NAME,
                (EmbeddedMongoInitializer.PROPERTY_NAMES): 'grails.mongodb.url, spring.data.mongodb.uri',
                'grails.mongodb.url'                     : 'mongodb://embedded:27984/bookstore',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then:
        String expected = 'mongodb://localhost:27984/bookstore'
        context.environment.getProperty('grails.mongodb.url') == expected
        context.environment.getProperty('spring.data.mongodb.uri') == expected
    }

    void 'a second context reuses the server the first one started'() {
        given:
        GenericApplicationContext first = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:27985/bookstore',
        ])
        new EmbeddedMongoInitializer().initialize(first)

        and:
        GenericApplicationContext restarted = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:27985/bookstore',
        ])

        when: 'the port is already owned, as it is after a devtools restart'
        new EmbeddedMongoInitializer().initialize(restarted)

        then:
        restarted.environment.getProperty('grails.mongodb.url') == 'mongodb://localhost:27985/bookstore'
    }

    void 'the in-memory backend refuses to pretend it can persist'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND)     : InMemoryMongoBackend.NAME,
                (EmbeddedMongoInitializer.DATABASE_DIR): temp.resolve('data').toString(),
                'grails.mongodb.url'                   : 'mongodb://embedded:27986/bookstore',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then: 'it names the backend that can, rather than silently discarding the data'
        IllegalStateException e = thrown()
        e.message.contains('cannot honour')
        e.message.contains('flapdoodle')
    }

    void 'a port held by something other than an embedded MongoDB is never reused'() {
        given: 'an unrelated service holding the port, on the address a backend binds'
        ServerSocket intruder = new ServerSocket(27990, 1, InetAddress.getByName('localhost'))
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:27990/bookstore',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then: 'it fails rather than publishing a MongoDB url pointing at that service'
        IllegalStateException e = thrown()
        e.message.contains('something else may already be using')

        and: 'the url still asks for a server rather than naming that one'
        context.environment.getProperty('grails.mongodb.url') == 'mongodb://embedded:27990/bookstore'
        context.environment.propertySources.every { it.name != 'embeddedMongoDB' }

        cleanup:
        intruder.close()
    }

    void 'the initializer is created without flapdoodle on the classpath'() {
        given: 'flapdoodle is compileOnly, so an application that has not added it sees this'
            ClassLoader withoutFlapdoodle = hiding('de.flapdoodle.')

        when: 'the backends are worked out the way the public constructor works them out'
            List<EmbeddedMongoBackend> backends =
                    EmbeddedMongoInitializer.defaultBackends(withoutFlapdoodle)

        then: 'holding the flapdoodle backend would load it, and loading it resolves the types its ' +
                'methods name -- so merely offering it threw NoClassDefFoundError and no embedded ' +
                'server could start at all'
            backends*.name == [InMemoryMongoBackend.NAME]
    }

    void 'flapdoodle is offered when it is on the classpath'() {
        expect:
            EmbeddedMongoInitializer.defaultBackends(getClass().classLoader)*.name ==
                    [FlapdoodleMongoBackend.NAME, InMemoryMongoBackend.NAME]
    }

    void 'asking for flapdoodle without it on the classpath says so'() {
        given:
            GenericApplicationContext context = new GenericApplicationContext()
            context.environment.propertySources.addFirst(new MapPropertySource('test', [
                    'embedded.mongodb.backend': FlapdoodleMongoBackend.NAME,
                    'grails.mongodb.url'      : 'mongodb://embedded:27992/bookstore',
            ]))

        when: 'the backend it asked for is not among the ones offered'
            new EmbeddedMongoInitializer([new InMemoryMongoBackend()]).initialize(context)

        then: 'which is a missing library rather than a name that means nothing'
            IllegalStateException e = thrown()
            e.message.contains('not on the classpath')

        cleanup:
            context.close()
    }

    /** A loader that cannot see the named package, standing in for it not being on the classpath. */
    private ClassLoader hiding(String packagePrefix) {
        new ClassLoader(getClass().classLoader) {
            @Override
            Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith(packagePrefix)) {
                    throw new ClassNotFoundException(name)
                }
                super.loadClass(name, resolve)
            }
        }
    }

    void 'a known backend whose library is missing names the ones that are left'() {
        given: 'mongo-java-server excluded from an application that still asked for it'
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded',
        ])

        when:
        new EmbeddedMongoInitializer([new MissingLibraryBackend(InMemoryMongoBackend.NAME),
                                      new FlapdoodleMongoBackend()]).initialize(context)

        then: 'the message points at the library, and at what could be used instead'
        IllegalStateException e = thrown()
        e.message.contains('not on the classpath')
        e.message.contains('choose one of [flapdoodle]')
    }

    void 'no backend at all is reported as the missing dependency it is'() {
        given: 'both libraries excluded, so nothing can serve the url this was asked to publish'
        GenericApplicationContext context = contextWith([
        'grails.mongodb.url'              : 'mongodb://embedded',
        ])

        when: 'no backend is named either, so this is the fall through rather than a bad choice'
        new EmbeddedMongoInitializer([]).initialize(context)

        then:
        IllegalStateException e = thrown()
        e.message.contains('no embedded MongoDB backend is on the classpath')
        e.message.contains('de.bwaldvogel:mongo-java-server')
    }

    void 'an unknown backend is reported by name'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): 'sqlite',
                'grails.mongodb.url'              : 'mongodb://embedded:27989/bookstore',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then:
        IllegalStateException e = thrown()
        e.message.contains('is not a known backend')
    }

    void 'the MongoDB port moves with the server port so two applications run side by side'() {
        given: 'the second application on a machine, which moved its own port to start at all'
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'server.port'                     : '9055',
                'grails.mongodb.url'              : 'mongodb://embedded',
        ])

        when: 'no MongoDB port is configured, so it follows'
        new EmbeddedMongoInitializer().initialize(context)

        then: '27017 offset by however far 8080 moved'
        context.environment.getProperty('grails.mongodb.url') == 'mongodb://localhost:27992/test'
    }

    void 'a url that names no database leaves the default in place'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:27987',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then: 'there was no database name to preserve, rather than an empty one to publish'
        context.environment.getProperty('grails.mongodb.url') == 'mongodb://localhost:27987/test'
    }

    void 'a property-names list of nothing but separators still publishes somewhere'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND)       : InMemoryMongoBackend.NAME,
                (EmbeddedMongoInitializer.PROPERTY_NAMES): ' , ',
                'grails.mongodb.url'              : 'mongodb://embedded:27988/bookstore',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then: 'a started server no application can reach is worse than falling back to the default'
        context.environment.getProperty('grails.mongodb.url') == 'mongodb://localhost:27988/bookstore'
    }

    void 'a setting left blank is a setting that was not made'() {
        given: 'the keys are present with nothing after them, which is what empty yaml entries give'
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND) : '',
                'server.port'                      : '9064',
                'grails.mongodb.url'              : 'mongodb://embedded',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then: 'rather than an unknown backend named the empty string, a port that will not parse, ' +
                'or a database with no name'
        context.environment.getProperty('grails.mongodb.url') == 'mongodb://localhost:28001/test'
    }

    void 'a backend whose library is missing is passed over rather than chosen'() {
        given: 'flapdoodle offered first, as it always is, but excluded by the application'
        GenericApplicationContext context = contextWith([
        'grails.mongodb.url'              : 'mongodb://embedded:28000',
        ])

        when: 'no backend is named, so the first one that can actually run is used'
        new EmbeddedMongoInitializer([new MissingLibraryBackend(FlapdoodleMongoBackend.NAME),
                                      new InMemoryMongoBackend()]).initialize(context)

        then:
        context.environment.getProperty('grails.mongodb.url') == 'mongodb://localhost:28000/test'
    }

    void 'a MongoDB version flapdoodle does not know is reported before anything is downloaded'() {
        given:
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): FlapdoodleMongoBackend.NAME,
                (EmbeddedMongoInitializer.VERSION): 'V9_9',
                'grails.mongodb.url'              : 'mongodb://embedded:27993',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then: 'the constant it needed, rather than a download that fails halfway'
        IllegalStateException e = thrown()
        e.message.contains('is not a Version.Main constant')
        e.message.contains('V8_0')

        and:
        !listening(27993)
    }

    void 'a database directory that cannot be created is reported by path'() {
        given: 'a file where the directory should be, which is how a mistyped path usually looks'
        Path file = temp.resolve('prodDb')
        file.toFile().text = 'not a directory'
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND)     : FlapdoodleMongoBackend.NAME,
                (EmbeddedMongoInitializer.DATABASE_DIR): file.toString(),
                'grails.mongodb.url'              : 'mongodb://embedded:27998',
        ])

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then: 'rather than a mongod that starts and writes where nobody looks'
        IllegalStateException e = thrown()
        e.message.contains('Could not create the embedded MongoDB directory')
        e.message.contains(file.toString())

        and:
        !listening(27998)
    }

    /** A backend whose library an application excluded, which only its absence distinguishes. */
    private static class MissingLibraryBackend implements EmbeddedMongoBackend {

        private final String name

        MissingLibraryBackend(String name) {
            this.name = name
        }

        @Override
        String getName() {
            name
        }

        @Override
        boolean isAvailable() {
            false
        }

        @Override
        RunningEmbeddedMongo start(EmbeddedMongoSettings settings) {
            throw new UnsupportedOperationException('not available')
        }
    }

    private static String roundTrip(String url) {
        try (MongoClient client = MongoClients.create(url)) {
            def collection = client.getDatabase('bookstore').getCollection('probe')
            collection.insertOne(new Document('backend', url.contains('27981') ? 'in-memory' : 'flapdoodle'))
            collection.find().first().getString('backend')
        }
    }

    void 'nothing is started while bean definitions are being generated'() {
        given: 'a configuration that would otherwise start a server, on a port no other feature ' +
                'here uses -- servers started in this spec outlive the feature that started them'
        assert !listening(27991)
        GenericApplicationContext context = contextWith([
                (EmbeddedMongoInitializer.BACKEND): InMemoryMongoBackend.NAME,
                'grails.mongodb.url'              : 'mongodb://embedded:27991/bookstore',
        ])
        System.setProperty(AbstractAotProcessor.AOT_PROCESSING, 'true')

        when:
        new EmbeddedMongoInitializer().initialize(context)

        then: 'generation reads definitions rather than running them, and a server that started ' +
                'would listen on a non-daemon thread and hang the build that started it'
        context.environment.getProperty('grails.mongodb.url') == 'mongodb://embedded:27991/bookstore'
        context.environment.propertySources.every { it.name != 'embeddedMongoDB' }

        and: 'nothing is listening on the port it was told to use'
        !listening(27991)

        cleanup:
        System.clearProperty(AbstractAotProcessor.AOT_PROCESSING)
    }

    private static boolean listening(int port) {
        try {
            new Socket('localhost', port).withCloseable { true }
        }
        catch (IOException ignored) {
            false
        }
    }

    private static GenericApplicationContext contextWith(Map<String, Object> properties) {
        GenericApplicationContext context = new GenericApplicationContext()
        context.environment.propertySources.addFirst(new MapPropertySource('test', properties))
        context
    }
}
