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

import grails.gorm.annotation.Entity
import grails.mongodb.MongoEntity
import org.apache.grails.testing.mongo.AutoStartedMongoSpec
import org.bson.types.ObjectId
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.config.MongoSettings
import spock.lang.Issue
import spock.lang.Shared

/**
 * A save through the API of a connection checks the {@code unique} constraint on that connection, not on the default one.
 */
@Issue('https://github.com/apache/grails-core/issues/16934')
class UniqueNamedConnectionMongoSpec extends AutoStartedMongoSpec {

    @Shared MongoDatastore datastore

    @Override
    boolean shouldInitializeDatastore() {
        false
    }

    void setupSpec() {
        datastore = new MongoDatastore([
                (MongoSettings.SETTING_URL)        : "mongodb://${mongoHost}:${mongoPort}/uniqueNamedDefaultDb" as String,
                (MongoSettings.SETTING_CONNECTIONS): [
                        archive: [url: "mongodb://${mongoHost}:${mongoPort}/uniqueNamedArchiveDb" as String]
                ]
        ], getDomainClasses() as Class[])
    }

    void cleanupSpec() {
        datastore.close()
    }

    void setup() {
        UniqueNamedItem.DB.drop()
        UniqueNamedItem.archive.DB.drop()
        new UniqueNamedItem(code: 'onDefault').save(flush: true)
        UniqueNamedItem.archive.save(new UniqueNamedItem(code: 'onArchive'), [flush: true])
    }

    void cleanup() {
        UniqueNamedItem.DB.drop()
        UniqueNamedItem.archive.DB.drop()
    }

    void "a save through the API of the archive connection checks the archive connection"() {
        when:
        UniqueNamedItem duplicate = UniqueNamedItem.archive.save(new UniqueNamedItem(code: 'onArchive'), [flush: true])
        UniqueNamedItem free = UniqueNamedItem.archive.save(new UniqueNamedItem(code: 'onDefault'), [flush: true])

        then: 'the value of the archive is refused and the one of the default connection is stored'
        duplicate == null
        free != null
        UniqueNamedItem.archive.count() == 2
    }

    void "a save through the default connection still checks the default connection"() {
        when:
        UniqueNamedItem duplicate = new UniqueNamedItem(code: 'onDefault').save(flush: true)
        UniqueNamedItem free = new UniqueNamedItem(code: 'onArchive').save(flush: true)

        then:
        duplicate == null
        free != null
    }

    List getDomainClasses() {
        [UniqueNamedItem]
    }
}

@Entity
class UniqueNamedItem implements MongoEntity<UniqueNamedItem> {
    ObjectId id
    String code

    static constraints = {
        code unique: true
    }

    static mapping = {
        connections ConnectionSource.DEFAULT, 'archive'
    }
}
