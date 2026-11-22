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

package org.grails.datastore.gorm

import grails.persistence.Entity
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * isEmpty and isNotEmpty on a property stored under another key than its name, at the root of a query and inside the
 * block of an association, on the in-memory store.
 */
@Issue('https://github.com/apache/grails-core/issues/14617')
class MappedKeyIsEmptySpec extends GrailsDataTckSpec {

    @Override
    void setupSpec() {
        manager.domainClasses.addAll([MappedKeyHolder, MappedKeyOwner])
    }

    private void seed() {
        MappedKeyHolder filled = new MappedKeyHolder(name: 'filled', note: 'text', tags: ['x', 'y']).save(failOnError: true)
        MappedKeyHolder bare = new MappedKeyHolder(name: 'bare', note: '').save(failOnError: true)
        MappedKeyHolder none = new MappedKeyHolder(name: 'none').save(failOnError: true)
        new MappedKeyOwner(label: 'with filled', holder: filled).save(failOnError: true)
        new MappedKeyOwner(label: 'with bare', holder: bare).save(failOnError: true)
        new MappedKeyOwner(label: 'with none', holder: none).save(flush: true, failOnError: true)
        manager.session.clear()
    }

    void "isEmpty and isNotEmpty on a collection stored under another key, at the root"() {
        given:
        seed()

        expect:
        MappedKeyHolder.createCriteria().list { isEmpty('tags') }*.name.sort() == ['bare', 'none']
        MappedKeyHolder.createCriteria().list { isNotEmpty('tags') }*.name == ['filled']
    }

    void "isEmpty and isNotEmpty on a string stored under another key, at the root"() {
        given:
        seed()

        expect:
        MappedKeyHolder.createCriteria().list { isEmpty('note') }*.name.sort() == ['bare', 'none']
        MappedKeyHolder.createCriteria().list { isNotEmpty('note') }*.name == ['filled']
    }

    void "a block on an association stored under another key reads it there, for every kind of criterion"() {
        given:
        seed()

        expect:
        MappedKeyOwner.createCriteria().list { holder { eq('name', 'filled') } }*.label == ['with filled']
        MappedKeyOwner.createCriteria().list { holder { ne('name', 'filled') } }*.label.sort() == ['with bare', 'with none']
        MappedKeyOwner.createCriteria().list { holder { isNotNull('name') } }*.label.sort() == ['with bare', 'with filled', 'with none']
        MappedKeyOwner.createCriteria().list { holder { like('name', 'fi%') } }*.label == ['with filled']
        MappedKeyOwner.createCriteria().list { holder { isNull('note') } }*.label == ['with none']
    }

    void "isEmpty and isNotEmpty on a property stored under another key, inside a block"() {
        given:
        seed()

        expect:
        MappedKeyOwner.createCriteria().list { holder { isEmpty('tags') } }*.label.sort() == ['with bare', 'with none']
        MappedKeyOwner.createCriteria().list { holder { isNotEmpty('tags') } }*.label == ['with filled']
        MappedKeyOwner.createCriteria().list { holder { isEmpty('note') } }*.label.sort() == ['with bare', 'with none']
        MappedKeyOwner.createCriteria().list { holder { isNotEmpty('note') } }*.label == ['with filled']
    }
}

@Entity
class MappedKeyHolder {
    String name
    String note
    Set<String> tags

    static hasMany = [tags: String]

    static constraints = {
        note nullable: true
    }

    static mapping = {
        tags key: 'stored_tags'
        note key: 'stored_note'
    }
}

@Entity
class MappedKeyOwner {
    String label
    MappedKeyHolder holder

    static mapping = {
        holder key: 'stored_holder'
    }
}
