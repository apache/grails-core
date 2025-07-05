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
import org.apache.grails.data.simple.core.GrailsDataCoreTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

/**
 * @author Graeme Rocher
 */
class EnumHasManySpec extends GrailsDataTckSpec<GrailsDataCoreTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([Animal])
    }

    @Issue('GRAILS-9882')
    void "Test that a collection of enums can be persisted"() {
        when: "A domain class with a collection of enum instance is saved"
        Animal zebra = new Animal(name: 'zebra')
        zebra.addToTraits(Trait.FOUR_LEGS)
        zebra.addToTraits(Trait.TAIL)
        zebra.addToTraits(Trait.STRIPES)
        zebra.save(flush: true)
        manager.session.clear()

        then: "The results are correct"
        Animal.findByName('zebra').traits.size() == 3
    }

    void "Test removeFrom collection of enums"() {
        setup:
        Animal zebra = new Animal(name: 'zebra')
        zebra.addToTraits(Trait.FOUR_LEGS)
        zebra.addToTraits(Trait.TAIL)
        zebra.addToTraits(Trait.STRIPES)

        when:
        zebra.removeFromTraits(Trait.FOUR_LEGS)
        zebra.save(flush: true)

        then:
        zebra.traits.size() == 2
        !zebra.traits.contains(Trait.FOUR_LEGS)
        zebra.traits.contains(Trait.TAIL)
        zebra.traits.contains(Trait.STRIPES)
    }
}

@Entity
class Animal {

    Long id
    Set<Trait> traits
    static hasMany = [traits: Trait]

    String name
}

enum Trait {
    TAIL,
    FOUR_LEGS,
    SPOTS,
    STRIPES
}
