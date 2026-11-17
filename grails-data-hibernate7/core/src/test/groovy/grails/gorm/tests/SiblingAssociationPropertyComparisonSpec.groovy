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
package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity

/**
 * Hibernate does not compare entities of classes that are not related, so two associations to such classes are
 * compared by their identifiers, one property at a time when the identifier is composite, as Hibernate 5 compares the
 * columns of the foreign keys. An association compared with a value that is not an entity is compared by its
 * identifier.
 */
class SiblingAssociationPropertyComparisonSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.registerDomainClasses(SpcLeft, SpcRight, SpcPlainLeft, SpcPlainRight, SpcHolder)
    }

    private void seedComposite() {
        SpcLeft left = new SpcLeft(area: 'a', code: 'b').save(flush: true)
        SpcRight same = new SpcRight(area: 'a', code: 'b').save(flush: true)
        SpcRight other = new SpcRight(area: 'c', code: 'd').save(flush: true)
        new SpcHolder(name: 'same', left: left, right: same).save(flush: true)
        new SpcHolder(name: 'other', left: left, right: other).save(flush: true)
        manager.session.clear()
    }

    def 'two associations to sibling classes with a composite id are compared by their identifiers'() {
        given:
        seedComposite()

        expect:
        SpcHolder.withCriteria { eqProperty('left', 'right') }*.name == ['same']
        SpcHolder.withCriteria { neProperty('left', 'right') }*.name == ['other']
    }

    def 'two associations to sibling classes with a composite id are compared by their identifiers in a detached criteria'() {
        given:
        seedComposite()

        expect:
        new DetachedCriteria(SpcHolder).build { eqProperty('left', 'right') }.list()*.name == ['same']
    }

    def 'two associations to sibling classes with a generated id are compared by their identifiers'() {
        given:
        SpcPlainLeft left = new SpcPlainLeft(name: 'l').save(flush: true)
        SpcPlainRight right = new SpcPlainRight(name: 'r').save(flush: true)
        new SpcHolder(name: 'plain', plainLeft: left, plainRight: right).save(flush: true)
        manager.session.clear()
        boolean sameId = left.id == right.id

        expect:
        SpcHolder.withCriteria { eqProperty('plainLeft', 'plainRight') }*.name == (sameId ? ['plain'] : [])
    }

    def 'an association with a composite id is not compared with one that has a generated id'() {
        given:
        SpcLeft left = new SpcLeft(area: 'a', code: 'b').save(flush: true)
        SpcPlainLeft plain = new SpcPlainLeft(name: 'l').save(flush: true)
        new SpcHolder(name: 'mixed', left: left, plainLeft: plain).save(flush: true)
        manager.session.clear()

        when:
        SpcHolder.withCriteria { eqProperty('left', 'plainLeft') }

        then: 'the message names both classes and the number of properties of each identifier'
        IllegalArgumentException e = thrown()
        e.message.contains('SpcLeft') && e.message.contains('SpcPlainLeft') && e.message.contains('2 and 1')
    }

    def 'an association compared with the identifier of the holder is still compared by its identifier'() {
        given:
        SpcPlainLeft left = new SpcPlainLeft(name: 'l').save(flush: true)
        SpcHolder holder = new SpcHolder(name: 'plain', plainLeft: left).save(flush: true)
        manager.session.clear()
        boolean sameId = left.id == holder.id

        expect:
        SpcHolder.withCriteria { eqProperty('plainLeft', 'id') }*.name == (sameId ? ['plain'] : [])
    }
}

@Entity
class SpcLeft implements Serializable {

    String area
    String code

    static mapping = {
        id composite: ['area', 'code']
        version false
    }

    @Override
    boolean equals(Object other) {
        other instanceof SpcLeft && other.area == area && other.code == code
    }

    @Override
    int hashCode() {
        Objects.hash(area, code)
    }
}

@Entity
class SpcRight implements Serializable {

    String area
    String code

    static mapping = {
        id composite: ['area', 'code']
        version false
    }

    @Override
    boolean equals(Object other) {
        other instanceof SpcRight && other.area == area && other.code == code
    }

    @Override
    int hashCode() {
        Objects.hash(area, code)
    }
}

@Entity
class SpcPlainLeft {
    String name
}

@Entity
class SpcPlainRight {
    String name
}

@Entity
class SpcHolder {

    String name
    SpcLeft left
    SpcRight right
    SpcPlainLeft plainLeft
    SpcPlainRight plainRight

    static constraints = {
        left(nullable: true)
        right(nullable: true)
        plainLeft(nullable: true)
        plainRight(nullable: true)
    }
}
