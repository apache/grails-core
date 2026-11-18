/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.orm.hibernate.query

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.dialect.H2Dialect
import org.hibernate.engine.jdbc.spi.JdbcServices
import org.hibernate.engine.spi.SessionFactoryImplementor
import org.hibernate.query.sqm.sql.SqmTranslatorFactory
import org.hibernate.query.sqm.sql.StandardSqmTranslatorFactory
import org.hibernate.query.sqm.tree.SqmDmlStatement
import org.hibernate.query.sqm.tree.select.SqmSelectStatement
import org.hibernate.sql.ast.spi.SqlAstCreationContext
import spock.lang.Specification

/**
 * GORM registers {@link RestrictedCollectionSqmTranslatorFactory} as the translator of the queries of a datastore,
 * unless the application configures a translator of its own.
 */
class RestrictedCollectionSqmTranslatorFactorySpec extends Specification {

    HibernateDatastore datastore

    void cleanup() {
        datastore?.destroy()
    }

    void 'a datastore translates its queries with the translator that applies collection restrictions'() {
        when:
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(settings('rcstDefault')), RcstOwner)

        then:
        translatorOf(datastore) instanceof RestrictedCollectionSqmTranslatorFactory
    }

    void 'a translator the application configures is kept'() {
        when:
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(settings('rcstCustom') +
                ['hibernate.query.sqm.translator': StandardSqmTranslatorFactory.name]), RcstOwner)

        then:
        translatorOf(datastore).getClass() == StandardSqmTranslatorFactory
    }

    void 'a dialect with a translator of its own keeps it'() {
        given: 'a dialect whose translator factory is its own'
        SqmTranslatorFactory own = Mock()
        H2Dialect dialect = new H2Dialect() {
            @Override
            SqmTranslatorFactory getSqmTranslatorFactory() {
                own
            }
        }
        SqlAstCreationContext creationContext = Stub {
            getSessionFactory() >> Stub(SessionFactoryImplementor) {
                getJdbcServices() >> Stub(JdbcServices) {
                    getDialect() >> dialect
                }
            }
        }
        SqmSelectStatement<?> select = Stub()
        SqmDmlStatement<?> update = Stub()

        when:
        new RestrictedCollectionSqmTranslatorFactory().createSelectTranslator(select, null, null, null, null,
                creationContext, true)
        new RestrictedCollectionSqmTranslatorFactory().createMutationTranslator(update, null, null, null, null,
                creationContext)

        then: 'both translations are the dialect\'s'
        1 * own.createSelectTranslator(select, null, null, null, null, creationContext, true)
        1 * own.createMutationTranslator(update, null, null, null, null, creationContext)
    }

    void 'is empty on a one-to-many restricted to a subclass keeps Hibernate\'s translation and its answer'() {
        given: 'a kennel with a cat and no dog, both rows of one table-per-hierarchy table'
        datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(settings('rcstKennel')),
                RcstKennel, RcstAnimal, RcstDog, RcstCat)
        RcstKennel.withNewTransaction {
            RcstKennel kennel = new RcstKennel(name: 'kennel').save(flush: true)
            new RcstCat(name: 'cat', kennel: kennel).save(flush: true)
        }

        expect: 'the restriction to the subclass holds: the cat is no dog'
        RcstKennel.withNewTransaction {
            RcstKennel.executeQuery('select k.name from RcstKennel k where k.dogs is empty') == ['kennel'] &&
                    RcstKennel.executeQuery('select k.name from RcstKennel k where k.dogs is not empty').isEmpty()
        }
    }

    private static Map<String, Object> settings(String database) {
        [
                'dataSource.url'     : "jdbc:h2:mem:${database};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate': 'create-drop',
                'dataSource.dialect' : H2Dialect.name,
        ]
    }

    private static SqmTranslatorFactory translatorOf(HibernateDatastore datastore) {
        ((SessionFactoryImplementor) datastore.sessionFactory).sessionFactoryOptions.customSqmTranslatorFactory
    }
}

@Entity
class RcstOwner {
    Long id
    String name
    static hasMany = [tags: String]
}

@Entity
class RcstKennel {
    Long id
    String name
    static hasMany = [dogs: RcstDog, cats: RcstCat]
}

@Entity
class RcstAnimal {
    Long id
    String name
    RcstKennel kennel
    static belongsTo = [kennel: RcstKennel]
}

@Entity
class RcstDog extends RcstAnimal {
}

@Entity
class RcstCat extends RcstAnimal {
}
