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
package org.grails.datastore.gorm.mongo

import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document
import org.bson.types.ObjectId
import org.grails.datastore.mapping.dirty.checking.DirtyCheckingMap
import spock.lang.Issue

/**
 * A Map property whose entries are named like bean properties of the map, as the {@code properties}
 * member of a GeoJSON Feature is, is stored and loaded like any other entry. Groovy 5 resolves a
 * String subscript on a Map as property access, so the codecs go through the Map API instead, and
 * the dirty-checking wrapper a loaded map comes back as resolves its own accessor names
 * ({@code target}, {@code parent}, {@code property}, {@code originalSize}, {@code assigned}) to
 * the entries.
 */
@Issue('https://github.com/apache/grails-core/issues/16675')
class MapKeysNamedLikeBeanPropertiesSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    static final Map FEATURE = [
            type        : 'Feature',
            geometry    : [type: 'Point', coordinates: [100.0d, 0.0d]],
            properties  : [name: 'Test', metaClass: 'nested'],
            metaClass   : 'top',
            firstKey    : 'first',
            target      : 't',
            parent      : 'p',
            property    : 'q',
            originalSize: 42,
            assigned    : 'yes'
    ]

    void setupSpec() {
        manager.registerDomainClasses(GeoFeature)
    }

    void "a Map property saved with a key named properties is read back with the entry intact"() {
        when: "an entity whose map holds keys named like bean properties is saved and reloaded"
        new GeoFeature(name: 'saved', geoJson: new LinkedHashMap(FEATURE)).save(flush: true, failOnError: true)
        manager.session.clear()
        GeoFeature loaded = GeoFeature.findByName('saved')

        then: "every entry survives the round trip"
        loaded.geoJson.get('properties') == [name: 'Test', metaClass: 'nested']
        loaded.geoJson.get('metaClass') == 'top'
        loaded.geoJson.get('firstKey') == 'first'
        loaded.geoJson == FEATURE

        and: "entries named like the accessors of the loaded map's wrapper read as entries"
        loaded.geoJson instanceof DirtyCheckingMap
        loaded.geoJson.target == 't'
        loaded.geoJson['parent'] == 'p'
        loaded.geoJson.property == 'q'
        loaded.geoJson['originalSize'] == 42
        loaded.geoJson.assigned == 'yes'

        when: "such an entry is written through the subscript form and the entity saved"
        loaded.geoJson['parent'] = 'changed'
        loaded.save(flush: true, failOnError: true)
        manager.session.clear()
        GeoFeature reloaded = GeoFeature.findByName('saved')

        then: "the write was tracked and persisted"
        reloaded.geoJson.get('parent') == 'changed'
        reloaded.geoJson.size() == FEATURE.size()
    }

    void "a Map property assigned on update with a key named properties is read back with the entry intact"() {
        given: "a persistent entity without the map"
        GeoFeature feature = new GeoFeature(name: 'updated').save(flush: true, failOnError: true)

        when: "the map is assigned and the update is flushed"
        feature.geoJson = new LinkedHashMap(FEATURE)
        feature.markDirty('geoJson')
        feature.save(flush: true, failOnError: true)
        manager.session.clear()
        GeoFeature loaded = GeoFeature.findByName('updated')

        then:
        loaded.geoJson.get('properties') == [name: 'Test', metaClass: 'nested']
        loaded.geoJson == FEATURE
    }

    void "a document written by the driver decodes a nested document under a key named properties"() {
        given: "a document written without GORM"
        Document geoJson = new Document('type', 'Feature')
                .append('geometry', new Document('type', 'Point').append('coordinates', [100.0d, 0.0d]))
                .append('properties', new Document('name', 'Test'))
                .append('metaClass', 'top')
        GeoFeature.collection.insertOne(new Document('name', 'raw').append('geoJson', geoJson))

        when:
        GeoFeature loaded = GeoFeature.findByName('raw')

        then:
        loaded.geoJson.get('properties') == [name: 'Test']
        loaded.geoJson.get('metaClass') == 'top'
        loaded.geoJson.get('geometry') == [type: 'Point', coordinates: [100.0d, 0.0d]]

        and: "the nested entry is reachable by a dotted query path"
        GeoFeature.createCriteria().get { eq('geoJson.properties.name', 'Test') }?.name == 'raw'
    }
}

@Entity
class GeoFeature {
    ObjectId id
    String name
    Map geoJson
}
