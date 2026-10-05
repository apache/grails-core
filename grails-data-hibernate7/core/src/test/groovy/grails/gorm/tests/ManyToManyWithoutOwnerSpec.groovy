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

import grails.gorm.annotation.Entity
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.engine.spi.SessionImplementor
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.sql.ResultSet

/**
 * A many-to-many where neither side declares belongsTo used to have no owning side: both collections were bound
 * inverse, so nothing was ever written to the join table and the relationship silently disappeared (Hibernate 5
 * behaves the same). One side is now chosen to own it, so the relationship is stored.
 */
class ManyToManyWithoutOwnerSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(
            NoOwnerLeft, NoOwnerRight, OwnedLeft, OwnerRight)

    void "a many-to-many with no belongsTo on either side persists its rows through the chosen owner"() {
        when:
        NoOwnerLeft.withNewTransaction {
            NoOwnerRight right = new NoOwnerRight(name: 'right').save(failOnError: true)
            NoOwnerLeft left = new NoOwnerLeft(name: 'left')
            left.addToRights(right)
            left.save(failOnError: true)
        }

        then:
        NoOwnerLeft.withNewSession { NoOwnerLeft.findByName('left').rights*.name } == ['right']
        NoOwnerRight.withNewSession { NoOwnerRight.findByName('right').lefts*.name } == ['left']
        joinTableNames().findAll { it.startsWith('NO_OWNER_') && it != 'NO_OWNER_LEFT' && it != 'NO_OWNER_RIGHT' } ==
                ['NO_OWNER_LEFT_RIGHTS'] as Set
    }

    void "belongsTo on one side still decides the owner"() {
        when:
        OwnerRight.withNewTransaction {
            OwnerRight owner = new OwnerRight(name: 'owner')
            owner.addToLefts(new OwnedLeft(name: 'owned'))
            owner.save(failOnError: true)
        }

        then:
        OwnedLeft.withNewSession { OwnedLeft.findByName('owned').owners*.name } == ['owner']
        OwnerRight.withNewSession { OwnerRight.findByName('owner').lefts*.name } == ['owned']
    }

    private Set<String> joinTableNames() {
        SessionImplementor session = (SessionImplementor) datastore.sessionFactory.openSession()
        try {
            session.doReturningWork { connection ->
                Set<String> names = []
                try (def statement = connection.createStatement()) {
                    try (ResultSet resultSet = statement.executeQuery('select table_name from information_schema.tables')) {
                        while (resultSet.next()) {
                            names << resultSet.getString(1).toUpperCase()
                        }
                    }
                }
                names
            }
        } finally {
            session.close()
        }
    }
}

@Entity
class NoOwnerLeft {
    String name
    Set<NoOwnerRight> rights

    static hasMany = [rights: NoOwnerRight]
}

@Entity
class NoOwnerRight {
    String name
    Set<NoOwnerLeft> lefts

    static hasMany = [lefts: NoOwnerLeft]
}

@Entity
class OwnedLeft {
    String name
    Set<OwnerRight> owners

    static hasMany = [owners: OwnerRight]
    static belongsTo = [owners: OwnerRight]
}

@Entity
class OwnerRight {
    String name
    Set<OwnedLeft> lefts

    static hasMany = [lefts: OwnedLeft]
}
