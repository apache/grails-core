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

import grails.gorm.DetachedCriteria
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.Document

class EmbeddedNegationReproSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([RpOwner])
    }

    void 'a negated condition on an embedded value'() {
        given: 'the owners are written to the collection as documents'
        def owners = RpOwner.DB.getCollection('rp_owners')
        owners.deleteMany(new Document())
        owners.insertOne(new Document('name', 'Amelie').append('addr', new Document('town', 'Paris')))
        owners.insertOne(new Document('name', 'Bob').append('addr', new Document('town', 'Rome')))
        owners.insertOne(new Document('name', 'NoAddr'))
        owners.insertOne(new Document('name', 'AddrNull').append('addr', null))
        owners.insertOne(new Document('name', 'AddrEmpty').append('addr', new Document()))
        owners.insertOne(new Document('name', 'TownNull').append('addr', new Document('town', null)))
        manager.session.clear()

        and: 'the same question asked six ways'
        Map<String, Closure> queries = [
            'address { ne }'                  : { -> RpOwner.createCriteria().list { address { ne('city', 'Paris') } } },
            'address { not { inList } }'      : { -> RpOwner.createCriteria().list { address { not { inList('city', ['Paris']) } } } },
            'not { address { eq } }'          : { -> RpOwner.createCriteria().list { not { address { eq('city', 'Paris') } } } },
            'DetachedCriteria address { ne }' : { -> new DetachedCriteria(RpOwner).build { address { ne('city', 'Paris') } }.list() },
            'where address.city != Paris'     : { -> RpOwner.where { address.city != 'Paris' }.list() },
            'where !(address.city in [Paris])': { -> RpOwner.where { !(address.city in ['Paris']) }.list() },
        ]

        when: 'Bob, whose city is not Paris, should be returned, and NoAddr and AddrNull, who have no address at all, should not'
        List<String> wrong = queries.findAll { label, query -> !right(query.call()) }.keySet().toList()

        then: 'no query returns the wrong owners'
        wrong == []
    }

    private static boolean right(List<RpOwner> owners) {
        List<String> names = owners*.name
        names.contains('Bob') && !names.contains('NoAddr') && !names.contains('AddrNull')
    }
}

@Entity
class RpOwner {

    String id
    String name
    RpAddress address

    static mapWith = 'mongo'

    static embedded = ['address']

    static constraints = {
        address nullable: true
    }

    static mapping = {
        collection 'rp_owners'
        address field: 'addr'
    }
}

@Entity
class RpAddress {

    String city

    static mapWith = 'mongo'

    static mapping = {
        city field: 'town'
    }
}
