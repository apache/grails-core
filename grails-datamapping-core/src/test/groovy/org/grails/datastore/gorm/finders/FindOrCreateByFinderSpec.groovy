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
package org.grails.datastore.gorm.finders

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

class FindOrCreateByFinderSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(FindOrCreateByFinderThing)

    void "findOrCreateBy constructs but does not persist a new instance when no match exists"() {
        when:
        def result = FindOrCreateByFinderThing.findOrCreateByTitle('brand new')

        then: "unlike findOrSaveBy, the instance is only constructed in memory - no id assigned"
        result != null
        result.title == 'brand new'
        result.id == null
    }

    void "findOrCreateBy returns the existing match without creating a duplicate"() {
        given:
        def existing = FindOrCreateByFinderThing.newInstance(title: 'already here').save(flush: true)

        when:
        def result = FindOrCreateByFinderThing.findOrCreateByTitle('already here')

        then:
        result.title == 'already here'
        result.id == existing.id
    }

    void "findOrCreateBy rejects range-based expressions with a ConfigurationException"() {
        when:
        FindOrCreateByFinderThing.findOrCreateByAgeGreaterThan(5)

        then:
        thrown(ConfigurationException)
    }

    void "findOrCreateBy rejects an Or-joined expression with a MissingMethodException"() {
        when:
        FindOrCreateByFinderThing.findOrCreateByTitleOrAge('brand new', 5)

        then:
        thrown(MissingMethodException)
    }

    void "the (String, Datastore) constructor is usable directly"() {
        expect:
        new FindOrCreateByFinder(FindOrCreateByFinder.METHOD_PATTERN, datastore) != null
    }

    void "the (String, DatastoreResolver, MappingContext) constructor is usable directly"() {
        given:
        def resolver = { datastore } as DatastoreResolver

        expect:
        new FindOrCreateByFinder(FindOrCreateByFinder.METHOD_PATTERN, resolver, datastore.mappingContext) != null
    }

    void "the (MappingContext) constructor is usable directly"() {
        expect:
        new FindOrCreateByFinder(datastore.mappingContext) != null
    }
}

@Entity
class FindOrCreateByFinderThing {
    String title
    Integer age
}
