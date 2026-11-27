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
package org.grails.datastore.mapping.mongo.config

import com.mongodb.client.MongoClient
import com.mongodb.ReadPreference
import com.mongodb.MongoClientSettings
import com.mongodb.event.CommandListener
import org.apache.grails.data.mongodb.core.querytail.QueryTailCommandListener
import org.apache.grails.datamapping.core.querytail.QueryTailRecord
import org.apache.grails.datastore.core.querytail.QueryTailSettings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.connections.ConnectionSources
import org.grails.datastore.mapping.core.connections.ConnectionSourcesInitializer
import org.grails.datastore.mapping.mongo.connections.MongoClientSettingsBuilderCustomizer
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSource
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceFactory
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Created by graemerocher on 30/06/16.
 */
class MongoConnectionSourceFactorySpec extends Specification {

    @TempDir
    File tempDir

    void "the query-tail settings are read from grails.gorm.queryTail, every connection source inherits them, and each registers a listener"() {
        given:
        File file = new File(tempDir, 'capture.log')
        MongoConnectionSourceFactory factory = new MongoConnectionSourceFactory()

        when:
        ConnectionSources<MongoClient, MongoConnectionSourceSettings> sources = ConnectionSourcesInitializer.create(factory,
                DatastoreUtils.createPropertyResolver(
                        (MongoSettings.SETTING_URL): 'mongodb://localhost/myDb',
                        (MongoSettings.SETTING_CONNECTIONS): [another: [url: 'mongodb://localhost/anotherDb']],
                        (QueryTailSettings.SETTING_ENABLED): 'true',
                        (QueryTailSettings.SETTING_FILE): file.path))
        MongoConnectionSource runtime = sources.addConnectionSource('runtime', [url: 'mongodb://localhost/runtimeDb']) as MongoConnectionSource
        List<MongoConnectionSource> all = sources.allConnectionSources.collect { it as MongoConnectionSource }

        then:
        all*.name as Set == [ConnectionSource.DEFAULT, 'another', 'runtime'] as Set
        all.every { it.settings.queryTail.enabled && it.settings.queryTail.file == file.path }
        all.every { it.queryTail.file == file && it.queryTail.dataSourceName == it.name }
        runtime.queryTail.dataSourceName == 'runtime'

        cleanup:
        sources?.close()
    }

    void "without the setting no connection source registers a listener"() {
        when:
        ConnectionSources<MongoClient, MongoConnectionSourceSettings> sources = ConnectionSourcesInitializer.create(
                new MongoConnectionSourceFactory(), DatastoreUtils.createPropertyResolver((MongoSettings.SETTING_URL): 'mongodb://localhost/myDb'))

        then:
        !sources.defaultConnectionSource.settings.queryTail.enabled
        (sources.defaultConnectionSource as MongoConnectionSource).queryTail == null
        tempDir.list().length == 0

        cleanup:
        sources?.close()
    }

    void "Test MongoDB connection sources factory creates the correct configuration"() {
        when:"A factory instance"
        MongoConnectionSourceFactory factory = new MongoConnectionSourceFactory()
        ConnectionSources<MongoClient, MongoConnectionSourceSettings> sources = ConnectionSourcesInitializer.create(factory, DatastoreUtils.createPropertyResolver(
                (MongoSettings.SETTING_URL): "mongodb://localhost/myDb",
                (MongoSettings.SETTING_CONNECTIONS): [
                        another:[
                                url:"mongodb://localhost/anotherDb"
                        ]
                ]
        ))

        then:"The connection sources are correct"
        sources.defaultConnectionSource.name == ConnectionSource.DEFAULT
        sources.defaultConnectionSource.settings.url.database == 'myDb'
        sources.allConnectionSources.size() == 2
        sources.getConnectionSource('another').settings.url.database == 'anotherDb'

        and: "each can have its client replaced after a checkpoint and restore"
        sources.allConnectionSources.every { it instanceof MongoConnectionSource }

        cleanup:
        sources?.close()
    }

    void "test mongo client settings builder with fallback"() {
        when:"using a property resolver"
        Map myMap = ['grails.mongodb.options.readPreference': 'secondary',
                     (MongoSettings.SETTING_URL): "mongodb://localhost/myDb",
                     'grails.mongodb.options.clusterSettings.maxWaitQueueSize': '10',
                     (MongoSettings.SETTING_CONNECTIONS): [
                             another:[
                                     url:"mongodb://localhost/anotherDb"
                             ]
                     ]]

        MongoConnectionSourceFactory factory = new MongoConnectionSourceFactory()
        ConnectionSources<MongoClient, MongoConnectionSourceSettings> sources = ConnectionSourcesInitializer.create(factory, DatastoreUtils.createPropertyResolver(myMap))


        then:"The connection sources are correct"
        sources.defaultConnectionSource.name == ConnectionSource.DEFAULT
        sources.defaultConnectionSource.settings.url.database == 'myDb'
        sources.defaultConnectionSource.settings.options.build().readPreference == ReadPreference.secondary()
        sources.allConnectionSources.size() == 2
        sources.getConnectionSource('another').settings.url.database == 'anotherDb'
        sources.getConnectionSource('another').settings.options.build().readPreference == ReadPreference.secondary()

        cleanup:
        sources?.close()
    }

    void "test a registered MongoClientSettingsBuilderCustomizer is applied to the default connection source"() {
        given: "a customizer that records its invocation and registers a command listener"
        CommandListener listener = new CommandListener() {}
        MongoClientSettings.Builder captured = null
        MongoClientSettingsBuilderCustomizer customizer = { MongoClientSettings.Builder builder ->
            captured = builder
            builder.addCommandListener(listener)
        }

        when: "the factory builds connection sources with the customizer registered"
        MongoConnectionSourceFactory factory = new MongoConnectionSourceFactory(clientSettingsCustomizers: [customizer])
        ConnectionSources<MongoClient, MongoConnectionSourceSettings> sources = ConnectionSourcesInitializer.create(factory, DatastoreUtils.createPropertyResolver(
                (MongoSettings.SETTING_URL): "mongodb://localhost/myDb"
        ))

        then: "the customizer was invoked and its command listener applied to the built client settings"
        sources.defaultConnectionSource != null
        captured != null
        captured.build().commandListeners.contains(listener)

        cleanup:
        sources?.close()
    }

    void "test multiple MongoClientSettingsBuilderCustomizers are applied in registration order"() {
        given: "two customizers that append to a shared list"
        List<String> order = []
        MongoClientSettingsBuilderCustomizer first = { MongoClientSettings.Builder builder -> order << 'first' }
        MongoClientSettingsBuilderCustomizer second = { MongoClientSettings.Builder builder -> order << 'second' }

        when: "the factory builds connection sources with both customizers registered"
        MongoConnectionSourceFactory factory = new MongoConnectionSourceFactory(clientSettingsCustomizers: [first, second])
        ConnectionSources<MongoClient, MongoConnectionSourceSettings> sources = ConnectionSourcesInitializer.create(factory, DatastoreUtils.createPropertyResolver(
                (MongoSettings.SETTING_URL): "mongodb://localhost/myDb"
        ))

        then: "both ran, in order"
        order == ['first', 'second']

        cleanup:
        sources?.close()
    }

    void "invalid customized settings close the listener registered before the client settings are built, which never opens the capture"() {
        given: 'a capture file another run wrote'
        File file = new File(tempDir, 'failed-capture.log')
        file.text = QueryTailRecord.header('earlier-run') + '\n'
        MongoClientSettings.Builder captured
        MongoClientSettingsBuilderCustomizer customizer = { MongoClientSettings.Builder builder ->
            captured = builder
            builder.applyToConnectionPoolSettings { pool -> pool.minSize(2).maxSize(1) }
        }
        MongoConnectionSourceFactory factory = new MongoConnectionSourceFactory(clientSettingsCustomizers: [customizer])
        MongoConnectionSourceSettings settings = new MongoConnectionSourceSettings()
        settings.queryTail = new QueryTailSettings(enabled: true, file: file.path)

        when:
        factory.create(ConnectionSource.DEFAULT, settings)

        then:
        IllegalStateException failure = thrown()
        failure.message.contains('maxSize >= minSize')

        when: 'the builder can be read again after correcting its invalid pool limits'
        captured.applyToConnectionPoolSettings { pool -> pool.minSize(0) }
        QueryTailCommandListener listener = captured.build().commandListeners.find { it instanceof QueryTailCommandListener } as QueryTailCommandListener

        then: 'the listener was closed before it opened, and opens nothing now'
        listener.capture == null
        listener.open() == null
        QueryTailRecord.parseHeader(file.readLines().first()) == 'earlier-run'

        when: 'a corrected configuration builds a connection source of its own'
        factory.clientSettingsCustomizers = []
        MongoConnectionSource source = factory.create(ConnectionSource.DEFAULT, settings) as MongoConnectionSource

        then: 'which leaves the file as it was until its capture is opened, as a datastore opens it once it is built'
        source.queryTail.capture == null
        QueryTailRecord.parseHeader(file.readLines().first()) == 'earlier-run'
        source.queryTail.open().open
        QueryTailRecord.parseHeader(file.readLines().first()) != 'earlier-run'

        cleanup:
        source?.close()
        listener?.close()
    }

    void "a connection source built and closed without being opened leaves the capture file as it found it"() {
        given:
        File file = new File(tempDir, 'capture.log')
        file.text = QueryTailRecord.header('earlier-run') + '\n' +
                new QueryTailRecord(timestamp: 1L, dataSource: ConnectionSource.DEFAULT, sql: 'find shop.books').toLine() + '\n'
        String earlier = file.text
        MongoConnectionSourceSettings settings = new MongoConnectionSourceSettings()
        settings.queryTail = new QueryTailSettings(enabled: true, file: file.path)

        when: 'a connection source is built only for its client, and closed'
        MongoConnectionSource source = new MongoConnectionSourceFactory().create(ConnectionSource.DEFAULT, settings) as MongoConnectionSource
        source.close()

        then:
        source.queryTail.capture == null
        file.text == earlier
    }
}
