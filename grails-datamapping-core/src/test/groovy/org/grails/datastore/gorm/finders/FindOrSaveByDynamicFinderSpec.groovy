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
import org.grails.datastore.mapping.simple.SimpleMapDatastore
import spock.lang.AutoCleanup
import spock.lang.Specification

/**
 * Drives {@code findOrSaveBy*} through a {@code SimpleMapDatastore}-backed entity: the composed
 * {@link SingleResultFinder#findOrSaveBy} shares its construct-on-miss logic with
 * {@code findOrCreateBy*} and differs only in saving the constructed instance.
 */
class FindOrSaveByDynamicFinderSpec extends Specification {

    @AutoCleanup
    SimpleMapDatastore datastore = new SimpleMapDatastore(FindOrSaveByThing)

    void "findOrSaveBy creates and persists a new instance when no match exists"() {
        when:
        def result = FindOrSaveByThing.findOrSaveByTitle('brand new')

        then: "the new instance is actually saved (gets an id), unlike findOrCreateBy which only\n" +
                "constructs it in memory"
        result != null
        result.title == 'brand new'
        result.id != null
    }

    void "findOrSaveBy returns the existing match without creating a duplicate"() {
        given:
        def existing = FindOrSaveByThing.newInstance(title: 'already here').save(flush: true)

        when:
        def result = FindOrSaveByThing.findOrSaveByTitle('already here')

        then:
        result.title == 'already here'
        result.id == existing.id
    }
}

@Entity
class FindOrSaveByThing {
    String title
}
