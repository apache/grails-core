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
package org.grails.datastore.mapping.simple

import java.util.concurrent.ConcurrentHashMap

import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import spock.lang.Specification

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.gorm.events.ConfigurableApplicationContextEventPublisher
import org.grails.datastore.mapping.config.Settings
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext

class SimpleMapDatastoreConstructorsSpec extends Specification {

    void 'the no-argument constructor creates an empty datastore'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore()

        expect:
        datastore.backingMap.isEmpty()
        datastore.mappingContext.persistentEntities.isEmpty()
        datastore.connectionSources.defaultConnectionSource.name == ConnectionSource.DEFAULT

        cleanup:
        datastore.close()
    }

    void 'a datastore can be created for named data sources and entity classes'() {
        given:
        SimpleMapDatastore withNames = new SimpleMapDatastore(DatastoreUtils.createPropertyResolver(null), ['one', 'two'], SmcBook)

        expect:
        withNames.getDatastoreForConnection('one') instanceof SimpleMapDatastore
        withNames.getDatastoreForConnection('two') instanceof SimpleMapDatastore
        withNames.mappingContext.getPersistentEntity(SmcBook.name) != null

        cleanup:
        withNames.close()
    }

    void 'a datastore scans packages for entities with a property resolver and data source names'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore(DatastoreUtils.createPropertyResolver(null), ['one'], SmcBook.package)

        expect:
        datastore.mappingContext.getPersistentEntity(SmcBook.name) != null
        datastore.getDatastoreForConnection('one') instanceof SimpleMapDatastore

        cleanup:
        datastore.close()
    }

    void 'a datastore scans multiple packages for entities with a property resolver and data source names'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore(DatastoreUtils.createPropertyResolver(null), ['one'], [SmcBook.package] as Package[])

        expect:
        datastore.mappingContext.getPersistentEntity(SmcBook.name) != null

        cleanup:
        datastore.close()
    }

    void 'a datastore scans packages for entities with a configuration map and data source names'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore([:], ['one'], [SmcBook.package] as Package[])

        expect:
        datastore.mappingContext.getPersistentEntity(SmcBook.name) != null
        datastore.getDatastoreForConnection('one') instanceof SimpleMapDatastore

        cleanup:
        datastore.close()
    }

    void 'a datastore scans packages for entities with a configuration map'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore([(Settings.SETTING_FAIL_ON_ERROR): true], [SmcBook.package] as Package[])

        expect:
        datastore.mappingContext.getPersistentEntity(SmcBook.name) != null
        datastore.failOnError

        cleanup:
        datastore.close()
    }

    void 'a deprecated datastore backs onto the given map and publishes events through the application context'() {
        given:
        GenericApplicationContext ctx = new GenericApplicationContext()
        Map<String, Map> data = new ConcurrentHashMap<String, Map>()
        SimpleMapDatastore datastore = new SimpleMapDatastore(data, ctx)

        expect:
        datastore.backingMap.is(data)
        datastore.applicationEventPublisher instanceof ConfigurableApplicationContextEventPublisher

        cleanup:
        datastore.close()
    }

    void 'a deprecated datastore created from an application context without a property resolver bean uses defaults'() {
        given:
        GenericApplicationContext ctx = new GenericApplicationContext()
        SimpleMapDatastore datastore = new SimpleMapDatastore(ctx)

        expect:
        !datastore.failOnError
        datastore.applicationEventPublisher instanceof ConfigurableApplicationContextEventPublisher

        cleanup:
        datastore.close()
    }

    void 'a deprecated datastore created from an application context reads the property resolver bean'() {
        given:
        GenericApplicationContext ctx = new GenericApplicationContext()
        ctx.environment.propertySources.addFirst(new MapPropertySource('test', [(Settings.SETTING_FAIL_ON_ERROR): true]))
        ctx.refresh()
        SimpleMapDatastore datastore = new SimpleMapDatastore(ctx)

        expect:
        datastore.failOnError

        cleanup:
        datastore.close()
        ctx.close()
    }

    void 'a deprecated datastore can be created for a mapping context and an application context'() {
        given:
        GenericApplicationContext ctx = new GenericApplicationContext()
        KeyValueMappingContext mappingContext = new KeyValueMappingContext('test')
        mappingContext.addPersistentEntities(SmcBook)
        SimpleMapDatastore datastore = new SimpleMapDatastore(mappingContext, ctx)

        expect:
        datastore.mappingContext.is(mappingContext)
        datastore.applicationEventPublisher != null

        cleanup:
        datastore.close()
    }

    void 'entities added to the mapping context later are registered with GORM'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore(SmcBook)

        when:
        datastore.mappingContext.addPersistentEntity(SmcLater)

        then:
        GormEnhancer.findStaticApi(SmcLater).datastore.is(datastore)
        GormEnhancer.findInstanceApi(SmcLater) != null

        cleanup:
        datastore.close()
    }

    void 'the GORM APIs of the default connection are bound to the datastore itself'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore(['secondary'], SmcBook)

        expect:
        GormEnhancer.findStaticApi(SmcBook) instanceof GormStaticApi
        GormEnhancer.findStaticApi(SmcBook).datastore.is(datastore)
        GormEnhancer.findInstanceApi(SmcBook).datastore.is(datastore)
        GormEnhancer.findValidationApi(SmcBook) instanceof GormValidationApi

        cleanup:
        datastore.close()
    }

    void 'the GORM APIs of a named connection are bound to the datastore of that connection'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore(['secondary'], SmcBook)
        SimpleMapDatastore secondary = (SimpleMapDatastore) datastore.getDatastoreForConnection('secondary')

        expect:
        GormEnhancer.findStaticApi(SmcBook, 'secondary').datastore.is(secondary)
        GormEnhancer.findInstanceApi(SmcBook, 'secondary') instanceof GormInstanceApi
        GormEnhancer.findInstanceApi(SmcBook, 'secondary').datastore.is(secondary)
        GormEnhancer.findValidationApi(SmcBook, 'secondary').datastore.is(secondary)

        cleanup:
        datastore.close()
    }

    void 'nextIdentifier starts at one for an unknown family and continues from the size of an existing one'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore(SmcBook)

        expect:
        datastore.nextIdentifier('unknown') == 1L
        datastore.nextIdentifier('unknown') == 2L

        when:
        datastore.backingMap.put('filled', [a: 1, b: 2, c: 3])

        then:
        datastore.nextIdentifier('filled') == 4L

        cleanup:
        datastore.close()
    }

    void 'getDatastoreForConnection rejects a null connection name'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore(SmcBook)

        when:
        datastore.getDatastoreForConnection(null)

        then:
        ConfigurationException e = thrown()
        e.message == 'No datastore found for connection named [null]'

        cleanup:
        datastore.close()
    }

    void 'a tenant schema can be added to a datastore that has no entities'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore()

        when:
        datastore.addTenantForSchema('t1')

        then:
        datastore.getDatastoreForConnection('t1') instanceof SimpleMapDatastore

        cleanup:
        datastore.close()
    }

    void 'close reports a failure to destroy the datastore as an IOException'() {
        given:
        SimpleMapDatastore datastore = new SimpleMapDatastore(SmcBook) {
            @Override
            void destroy() {
                throw new IllegalStateException('boom')
            }
        }

        when:
        datastore.close()

        then:
        IOException e = thrown()
        e.cause instanceof IllegalStateException
        e.cause.message == 'boom'
    }

}

@Entity
class SmcBook {

    Long id
    String title

}

@Entity
class SmcLater {

    Long id

}
