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
package org.grails.datastore.mapping.mongo.connections

import java.util.function.Consumer

import ch.qos.logback.classic.Level
import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoClient
import org.apache.grails.data.mongodb.core.querytail.QueryTailCommandListener
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.grails.datastore.gorm.mongo.CapturedLog
import org.grails.datastore.mapping.core.connections.DefaultConnectionSource
import spock.lang.Specification
import spock.lang.TempDir

class MongoConnectionSourceSpec extends Specification {

    @TempDir
    File tempDir

    void 'it hands out the client it was built with, and owns it'() {
        given:
        MongoClient client = Mock(MongoClient)

        when:
        def source = new MongoConnectionSource('reporting', client, new MongoConnectionSourceSettings())

        then:
        source.name == 'reporting'
        source.source.is(client)
        source.closeable
    }

    void 'a replacement is handed out from then on, and the client it replaces is left for the caller'() {
        given:
        MongoClient original = Mock(MongoClient)
        MongoClient replacement = Mock(MongoClient)
        def source = new MongoConnectionSource('reporting', original, new MongoConnectionSourceSettings())

        when:
        source.replaceSource(replacement)

        then:
        source.source.is(replacement)
        0 * original.close()
    }

    void 'closing it closes the client in use, not the one it replaced'() {
        given:
        MongoClient original = Mock(MongoClient)
        MongoClient replacement = Mock(MongoClient)
        def source = new MongoConnectionSource('reporting', original, new MongoConnectionSourceSettings())
        source.replaceSource(replacement)

        when:
        source.close()

        then:
        1 * replacement.close()
        0 * original.close()
    }

    void 'one created with capture enabled registers a listener on the settings its client is built from, and owns both'() {
        given:
        MongoClient client = Mock(MongoClient)
        MongoClientSettings.Builder builder = MongoClientSettings.builder()

        when:
        MongoConnectionSource source = MongoConnectionSource.create('reporting', capturing(new File(tempDir, 'capture.log')),
                { Consumer<MongoClientSettings.Builder> gormSettings -> gormSettings.accept(builder); client })

        then:
        source.source.is(client)
        source.closeable
        source.queryTail.dataSourceName == 'reporting'
        builder.build().commandListeners == [source.queryTail]
    }

    void 'one created without capture registers nothing'() {
        given:
        MongoClient client = Mock(MongoClient)
        MongoClientSettings.Builder builder = MongoClientSettings.builder()

        when:
        MongoConnectionSource source = MongoConnectionSource.create('reporting', new MongoConnectionSourceSettings(),
                { Consumer<MongoClientSettings.Builder> gormSettings -> gormSettings.accept(builder); client })

        then:
        source.queryTail == null
        builder.build().commandListeners.isEmpty()
    }

    void 'a client that cannot be built leaves no listener behind'() {
        given:
        File file = new File(tempDir, 'capture.log')
        QueryTailCommandListener registered = null

        when:
        MongoConnectionSource.create('reporting', capturing(file), { Consumer<MongoClientSettings.Builder> gormSettings ->
            MongoClientSettings.Builder builder = MongoClientSettings.builder()
            gormSettings.accept(builder)
            registered = builder.build().commandListeners.first() as QueryTailCommandListener
            throw new IllegalStateException('cannot build the client')
        })

        then:
        thrown(IllegalStateException)
        registered.open() == null
        !file.exists()
    }

    void 'closing it closes the query-tail listener registered on its client, after the client'() {
        given:
        MongoClient client = Mock(MongoClient)
        MongoConnectionSource source = capturingSource(client, new File(tempDir, 'capture.log'))
        QueryTailCommandListener listener = source.queryTail

        expect:
        listener.open().open

        when:
        source.close()

        then:
        1 * client.close()
        !listener.capture.open
        source.closed
    }

    void 'closing it before its query-tail listener has opened the capture leaves the capture unopened for good'() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoConnectionSource source = capturingSource(Mock(MongoClient), file)
        QueryTailCommandListener listener = source.queryTail

        when:
        source.close()

        then:
        listener.capture == null
        listener.open() == null
        !file.exists()
    }

    void 'one adopting the client of a built one, with other settings, takes its listener too, which the built one no longer closes'() {
        given:
        MongoClient client = Mock(MongoClient)
        MongoConnectionSource built = capturingSource(client, new File(tempDir, 'capture.log'))
        QueryTailCommandListener listener = built.queryTail
        MongoConnectionSourceSettings settings = new MongoConnectionSourceSettings()

        when:
        MongoConnectionSource adopted = MongoConnectionSource.adopt(built, settings)

        then:
        adopted.name == 'reporting'
        adopted.source.is(client)
        adopted.settings.is(settings)
        adopted.queryTail.is(listener)
        built.queryTail == null

        when:
        listener.open()
        adopted.close()

        then:
        1 * client.close()
        !listener.capture.open
    }

    void 'the client of a built one handed out for its caller to close records nothing'() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoClient client = Mock(MongoClient)
        MongoConnectionSource built = capturingSource(client, file)
        QueryTailCommandListener listener = built.queryTail

        when:
        MongoClient detached = MongoConnectionSource.detach(built)

        then:
        detached.is(client)
        0 * client.close()
        built.queryTail == null
        listener.open() == null
        !file.exists()
    }

    void 'the client of a connection source of another kind is handed out as it is'() {
        given:
        MongoClient client = Mock(MongoClient)

        expect:
        MongoConnectionSource.detach(new DefaultConnectionSource<>('reporting', client, new MongoConnectionSourceSettings())).is(client)
    }

    void 'a replacement built in its place hands out its client and records through its listener, which joins the run of the one it replaces'() {
        given: 'a connection source whose listener writes a run'
        File file = new File(tempDir, 'capture.log')
        MongoClient closed = Mock(MongoClient)
        MongoClient replacement = Mock(MongoClient)
        MongoConnectionSource own = capturingSource(closed, file)
        QueryTailCommandListener previous = own.queryTail
        previous.open()
        MongoConnectionSource built = capturingSource(replacement, file)
        QueryTailCommandListener next = built.queryTail

        when: 'it is given the mapping the datastore knows, which a datastore built on no mapping knows none of'
        own.replaceWith(built, null)

        then: 'the new listener opened before the old one closed, so the file holds one run'
        own.source.is(replacement)
        own.queryTail.is(next)
        next.fields == null
        built.queryTail == null
        next.capture.open
        !previous.capture.open
        file.readLines().count { QueryTailRecord.parseHeader(it) } == 1
        0 * closed.close()

        cleanup:
        own.close()
    }

    void 'a replacement built without the listener is reported as capturing nothing, and the listener it replaces is closed'() {
        given: 'a connection source whose listener writes a run, and a replacement a factory built without the listener'
        File file = new File(tempDir, 'capture.log')
        MongoClient closed = Mock(MongoClient)
        MongoClient replacement = Mock(MongoClient)
        MongoConnectionSource own = capturingSource(closed, file)
        QueryTailCommandListener previous = own.queryTail
        previous.open()
        CapturedLog log = new CapturedLog(MongoConnectionSource.name, Level.WARN)

        when:
        own.replaceWith(new DefaultConnectionSource<>('reporting', replacement, new MongoConnectionSourceSettings()), null)

        then:
        own.source.is(replacement)
        own.queryTail == null
        !previous.capture.open
        log.events*.formattedMessage.any { it.contains('[reporting]') && it.contains('none of its commands is captured') }

        cleanup:
        log?.close()
        own?.close()
    }

    void 'a replacement of a connection source that captures nothing reports nothing'() {
        given:
        MongoConnectionSource own = new MongoConnectionSource('reporting', Mock(MongoClient), new MongoConnectionSourceSettings())
        CapturedLog log = new CapturedLog(MongoConnectionSource.name, Level.WARN)

        when:
        own.replaceWith(new DefaultConnectionSource<>('reporting', Mock(MongoClient), new MongoConnectionSourceSettings()), null)

        then:
        own.queryTail == null
        log.events.isEmpty()

        cleanup:
        log?.close()
    }

    void 'one built around a client the application supplied reports that it does not own it'() {
        given:
        MongoClient client = Mock(MongoClient)

        when:
        def source = new MongoConnectionSource('reporting', client, new MongoConnectionSourceSettings(), false)

        then:
        source.source.is(client)
        !source.closeable
    }

    void 'closing one that does not own its client leaves the client open'() {
        given:
        MongoClient client = Mock(MongoClient)
        def source = new MongoConnectionSource('reporting', client, new MongoConnectionSourceSettings(), false)

        when:
        source.close()

        then:
        0 * client.close()
    }

    void 'a replacement is handed out by one that does not own its client, and is not closed either'() {
        given:
        MongoClient original = Mock(MongoClient)
        MongoClient replacement = Mock(MongoClient)
        def source = new MongoConnectionSource('reporting', original, new MongoConnectionSourceSettings(), false)
        source.replaceSource(replacement)

        when:
        source.close()

        then:
        source.source.is(replacement)
        0 * replacement.close()
        0 * original.close()
    }

    void 'a missing replacement is refused'() {
        given:
        MongoClient client = Mock(MongoClient)
        def source = new MongoConnectionSource('reporting', client, new MongoConnectionSourceSettings())

        when:
        source.replaceSource(null)

        then:
        thrown(IllegalArgumentException)
        source.source.is(client)
    }

    private static MongoConnectionSourceSettings capturing(File file) {
        MongoConnectionSourceSettings settings = new MongoConnectionSourceSettings()
        settings.queryTail.enabled = true
        settings.queryTail.file = file.path
        settings
    }

    private static MongoConnectionSource capturingSource(MongoClient client, File file) {
        MongoConnectionSource.create('reporting', capturing(file),
                { Consumer<MongoClientSettings.Builder> gormSettings -> gormSettings.accept(MongoClientSettings.builder()); client })
    }
}
