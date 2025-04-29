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

import grails.gorm.dirty.checking.DirtyCheck
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId
import org.grails.datastore.mapping.dirty.checking.DirtyCheckable
import org.grails.datastore.mapping.mongo.config.MongoSettings
import spock.lang.Issue

/**
 * Created by graemerocher on 14/03/14.
 */
class DirtyCheckUpdateSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Bar])
    }

    @Issue('GPMONGODB-334')
    void "Test that dirty check works for simple lists"() {
        given: "A a domain instance"
        def b = new Bar(foo: "stuff", strings: ['a', 'b'])
        b.save(flush: true)
        manager.session.clear()

        when: "The list is updated"
        b = Bar.get(b.id)
        b.strings << 'c'
        b.version == 0
        b.save(flush: true)
        manager.session.clear()
        b = Bar.get(b.id)

        then: "the update was executed"
        b.version == 1
        b.strings.size() == 3
        b instanceof DirtyCheckable

        when:
        b.save(flush: true)
        manager.session.clear()
        b = Bar.get(b.id)

        then:
        b.version == 3 //should be 2
    }

    void "Test that the version is incremented on save"() {
        given: "A a domain instance"
        def b = new Bar(foo: "stuff", strings: ['a', 'b'])
        b.save(flush: true)
        manager.session.clear()

        when: "The list is updated"
        b = Bar.get(b.id)
        b.strings << 'c'
        b.save(flush: true)
        manager.session.clear()
        b = Bar.get(b.id)

        then: "the update was executed"
        b.strings.size() == 3
        b instanceof DirtyCheckable
    }
}

@Entity
@DirtyCheck
class Bar {
    ObjectId id

    String foo
    List<String> strings = new ArrayList()

}

