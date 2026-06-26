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

import com.mongodb.client.model.IndexOptions
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document
import org.bson.types.ObjectId

/**
 * Verifies that a $text index can be declared via the per-property mapping form
 * (indexAttributes: [type: 'text']) — including conventional auto-naming — and that an existing,
 * differently-named text index is absorbed via recreateOnConflict (MongoDB allows one text index
 * per collection, so the reconcile matches it by its synthetic {_fts,_ftsx} key, not by name).
 */
class TextIndexViaAttributesSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.registerDomainClasses(TextThing)
    }

    private void initIndexes() {
        def datastore = manager.mongoDatastore
        datastore.persistentEntityAdded(datastore.mappingContext.getPersistentEntity(TextThing.name))
    }

    void "Test that indexAttributes type:text creates a conventionally-named \$text index"() {
        given:
        initIndexes()
        def all = TextThing.collection.listIndexes().collect { [name: it.name, key: it.key] }
        def textIdx = all.find { it.key == [_fts: 'text', _ftsx: 1] }

        expect: "a real text index exists under the conventional name body_text (dump: ${all})"
        textIdx != null
        textIdx.name == 'body_text'
    }

    void "Test that a legacy differently-named text index is absorbed via recreateOnConflict"() {
        given: "simulate a pre-existing text index under a legacy custom name"
        def coll = TextThing.collection
        coll.listIndexes().findAll { it.key == [_fts: 'text', _ftsx: 1] }.each { coll.dropIndex(it.name as String) }
        coll.createIndex(new Document('body', 'text'), new IndexOptions().name('legacy_text_idx'))

        when: "indexes are re-initialised against the conventional declaration"
        initIndexes()

        then: "exactly one text index, now under the conventional name — legacy one dropped"
        def texts = coll.listIndexes().findAll { it.key == [_fts: 'text', _ftsx: 1] }
        texts.size() == 1
        texts[0].name == 'body_text'
    }
}


@Entity
class TextThing {
    ObjectId id
    Long version
    String body

    static mapping = {
        version false
        collection 'textthing'
        body index: true, indexAttributes: [type: 'text', recreateOnConflict: true]
    }
}
