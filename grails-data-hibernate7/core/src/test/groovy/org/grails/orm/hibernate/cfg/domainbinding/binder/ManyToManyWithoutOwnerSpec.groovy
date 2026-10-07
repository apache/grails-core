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
package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.annotation.Entity
import grails.unbootable.NoOwnerLeft
import grails.unbootable.NoOwnerRight
import grails.unbootable.NoOwnerSelf
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.MappingException
import org.hibernate.dialect.H2Dialect
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

/**
 * A bidirectional many-to-many is written by the side that {@code belongsTo} designates as the owner. When neither side
 * declares it, both collections are inverse and the relationship is never stored, so the application refuses to start
 * instead of silently losing the data, in both the domain binder and the generated mapping.
 */
class ManyToManyWithoutOwnerSpec extends Specification {

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(boolean generated, Class... classes) {
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                'dataSource.url'                  : "jdbc:h2:mem:m2mNoOwner${generated}${System.nanoTime()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'dataSource.dialect'              : H2Dialect.name,
                'hibernate.hbm2ddl.auto'          : 'create-drop',
                'hibernate.cache.queries'         : 'false',
                'hibernate.cache.use_query_cache' : 'false',
                'hibernate.generatedDomainClasses': generated,
        ]), classes)
        return datastore
    }

    private static MappingException mappingFailure(Closure<?> boot) {
        try {
            boot()
        } catch (Throwable e) {
            for (Throwable cause = e; cause != null; cause = cause.cause == cause ? null : cause.cause) {
                if (cause instanceof MappingException) {
                    return (MappingException) cause
                }
            }
            throw e
        }
        return null
    }

    private static UnsupportedOperationException refusalFailure(Closure<?> boot) {
        try {
            boot()
        } catch (Throwable e) {
            for (Throwable cause = e; cause != null; cause = cause.cause == cause ? null : cause.cause) {
                if (cause instanceof UnsupportedOperationException) {
                    return (UnsupportedOperationException) cause
                }
            }
            throw e
        }
        return null
    }

    @Unroll
    def "the application does not start when neither side of a many-to-many declares belongsTo (generated mapping: #generated)"() {
        when:
        MappingException failure = mappingFailure { boot(generated, NoOwnerLeft, NoOwnerRight) }

        then: 'the message names both sides and says where to declare belongsTo'
        failure != null
        failure.message == 'Neither side of the many-to-many between [grails.unbootable.NoOwnerLeft.rights] and ' +
                '[grails.unbootable.NoOwnerRight.lefts] declares belongsTo, so the relationship would never be stored. ' +
                'Declare belongsTo on the owned side, for example in NoOwnerRight: static belongsTo = NoOwnerLeft'

        where:
        generated << [false, true]
    }

    @Unroll
    def "the application does not start when a self-referencing many-to-many has no belongsTo (generated mapping: #generated)"() {
        when:
        MappingException failure = mappingFailure { boot(generated, NoOwnerSelf) }

        then:
        failure != null
        failure.message.contains('[grails.unbootable.NoOwnerSelf.followers] and [grails.unbootable.NoOwnerSelf.following]')
        failure.message.contains('static belongsTo = NoOwnerSelf')

        where:
        generated << [false, true]
    }

    @Unroll
    def "a many-to-many with belongsTo on one side starts and stores its rows (generated mapping: #generated)"() {
        when:
        boot(generated, OwnedLeft, OwnerRight)
        OwnerRight.withTransaction {
            OwnerRight owner = new OwnerRight(name: 'owner')
            owner.addToLefts(new OwnedLeft(name: 'left'))
            owner.save(flush: true, failOnError: true)
        }

        then:
        OwnerRight.withNewSession { OwnerRight.first().lefts*.name } == ['left']
        OwnedLeft.withNewSession { OwnedLeft.first().owners*.name } == ['owner']

        where:
        generated << [false, true]
    }

    @Unroll
    def "a unidirectional many-to-many starts (generated mapping: #generated)"() {
        when:
        boot(generated, UnidirectionalLeft, UnidirectionalRight)

        then:
        noExceptionThrown()

        where:
        generated << [false, true]
    }

    @Unroll
    def "a self-referencing many-to-many with belongsTo starts and stores its rows (generated mapping: #generated)"() {
        when:
        boot(generated, OwnedSelf)
        OwnedSelf.withTransaction {
            OwnedSelf first = new OwnedSelf(name: 'first')
            first.addToFollowers(new OwnedSelf(name: 'second'))
            first.save(flush: true, failOnError: true)
        }

        then:
        OwnedSelf.withNewSession { OwnedSelf.findByName('first').followers*.name } == ['second']

        where:
        generated << [false, true]
    }

    @Unroll
    def "a many-to-many with a Map on #shape starts without belongsTo and stores its rows through the Map side"() {
        when:
        boot(false, left, right)
        left.withTransaction {
            def rightRow = right.newInstance(name: 'right').save(failOnError: true)
            left.newInstance(name: 'left', (leftProperty): [first: rightRow]).save(flush: true, failOnError: true)
        }

        then:
        left.withNewSession { left.findByName('left')."$leftProperty".keySet() } == ['first'] as Set
        !readsBack || right.withNewSession { right.findByName('right')."$rightProperty"*.name } == ['left']

        where:
        shape        | left       | right       | leftProperty | rightProperty | readsBack
        'one side'   | MapSetLeft | MapSetRight | 'rights'     | 'lefts'       | true
        'both sides' | MapMapLeft | MapMapRight | 'rights'     | 'lefts'       | false
    }

    @Unroll
    def "the generated mapping refuses a many-to-many with a Map on #shape as a map on a many-to-many, not as a missing belongsTo"() {
        when:
        UnsupportedOperationException refusal = refusalFailure { boot(true, left, right) }

        then:
        refusal != null
        refusal.message.contains('a map on a many-to-many, which the generator does not support yet')

        where:
        shape        | left       | right
        'one side'   | MapSetLeft | MapSetRight
        'both sides' | MapMapLeft | MapMapRight
    }

    def "a Set on both sides without belongsTo still does not start"() {
        when:
        MappingException failure = mappingFailure { boot(false, NoOwnerLeft, NoOwnerRight) }

        then:
        failure != null
        failure.message.startsWith('Neither side of the many-to-many')
    }
}

@Entity
class OwnedLeft {

    String name
    Set<OwnerRight> owners

    static hasMany = [owners: OwnerRight]
    static belongsTo = OwnerRight
}

@Entity
class OwnerRight {

    String name
    Set<OwnedLeft> lefts

    static hasMany = [lefts: OwnedLeft]
}

@Entity
class UnidirectionalLeft {

    String name
    Set<UnidirectionalRight> rights

    static hasMany = [rights: UnidirectionalRight]
}

@Entity
class UnidirectionalRight {

    String name
}

@Entity
class OwnedSelf {

    String name
    Set<OwnedSelf> followers
    Set<OwnedSelf> following

    static hasMany = [followers: OwnedSelf, following: OwnedSelf]
    static mappedBy = [followers: 'following', following: 'followers']
    static belongsTo = [OwnedSelf]
}

@Entity
class MapSetLeft {

    String name
    Map<String, MapSetRight> rights

    static hasMany = [rights: MapSetRight]
}

@Entity
class MapSetRight {

    String name
    Set<MapSetLeft> lefts

    static hasMany = [lefts: MapSetLeft]
}

@Entity
class MapMapLeft {

    String name
    Map<String, MapMapRight> rights

    static hasMany = [rights: MapMapRight]
}

@Entity
class MapMapRight {

    String name
    Map<String, MapMapLeft> lefts

    static hasMany = [lefts: MapMapLeft]
}
