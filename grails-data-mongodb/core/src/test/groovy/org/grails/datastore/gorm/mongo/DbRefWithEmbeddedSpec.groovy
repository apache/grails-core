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

import com.mongodb.DBRef
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document
import org.bson.types.ObjectId
import spock.lang.Issue

/**
 * @author Graeme Rocher
 */
class DbRefWithEmbeddedSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.domainClasses.addAll([One, Two])
    }

    @Issue('GPMONGODB-260')
    void "Test that an embedded links to the correct collection when using dbrefs"() {
        when: ""
        def one = new One(name: 'My Foo')
        one.save(flush: true)

        def two = new Two()
        two.link2one = new Link2One(link: one)
        two.save(flush: true)
        manager.session.clear()
        final link2one = Two.collection.find().first().link2one?.link
        then: ""
        link2one instanceof DBRef
        Two.DB.getCollection(link2one.collectionName).find(new Document('_id', link2one.id)).first().name == "My Foo"

        when: "The entity is loaded again"
        two = Two.first()

        then: "It is correct"
        two.link2one.link.name == 'My Foo'
    }
}

@Entity
class One {
    ObjectId id
    String name
    static mapping = {
        version false
    }
}

@Entity
class Two {
    ObjectId id
    Link2One link2one
    static embedded = ['link2one']
    static mapping = {
        version false
    }
}

class Link2One {
    One link
    static mapping = {
        version false
        link reference: true
    }
}
