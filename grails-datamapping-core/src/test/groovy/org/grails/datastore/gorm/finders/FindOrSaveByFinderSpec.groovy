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
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * The diff simplified this class drastically - dropped a whole bespoke {@code doInvokeInternal}
 * override (~25 lines of manual property-map construction and constructor invocation via
 * {@code MetaClass}) in favour of just overriding {@code shouldSaveOnCreate()} on the shared
 * {@code FindOrCreateByFinder} base, plus added 3 new constructor overloads
 * ({@code (String, Datastore)}, {@code (String, DatastoreResolver, MappingContext)},
 * {@code (MappingContext)}) matching sibling finder classes' shape. The 2-arg
 * {@code (DatastoreResolver, MappingContext)} and single-arg {@code (Datastore)} constructors are
 * the ones `DefaultGormApiFactory`/`GormEnhancer` actually register in production and were already
 * covered incidentally; the 3 new overloads have no callers anywhere in the codebase.
 */
class FindOrSaveByFinderSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(FindOrSaveByFinderThing)

    void "findOrSaveBy creates and persists a new instance when no match exists"() {
        when:
        def result = FindOrSaveByFinderThing.findOrSaveByTitle('brand new')

        then: "shouldSaveOnCreate()==true means the new instance is actually saved (gets an id),\n" +
                "unlike findOrCreateBy which only constructs it in memory"
        result != null
        result.title == 'brand new'
        result.id != null
    }

    void "findOrSaveBy returns the existing match without creating a duplicate"() {
        given:
        def existing = FindOrSaveByFinderThing.newInstance(title: 'already here').save(flush: true)

        when:
        def result = FindOrSaveByFinderThing.findOrSaveByTitle('already here')

        then:
        result.title == 'already here'
        result.id == existing.id
    }

    void "the (String, Datastore) constructor is usable directly"() {
        expect:
        new FindOrSaveByFinder(FindOrSaveByFinder.METHOD_PATTERN, datastore) != null
    }

    void "the (String, DatastoreResolver, MappingContext) constructor is usable directly"() {
        given:
        def resolver = { datastore } as DatastoreResolver

        expect:
        new FindOrSaveByFinder(FindOrSaveByFinder.METHOD_PATTERN, resolver, datastore.mappingContext) != null
    }

    void "the (MappingContext) constructor is usable directly"() {
        expect:
        new FindOrSaveByFinder(datastore.mappingContext) != null
    }
}

@Entity
class FindOrSaveByFinderThing {
    String title
}
