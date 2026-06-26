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
import grails.gorm.transactions.Rollback
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 24/10/16.
 */
class DetachedCriteriaProjectionSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(Entity1, Entity2, DetachedEntity)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    @Transactional
    def setup() {
        DetachedEntity.withSession { session ->
            DetachedEntity.findAll().each { it.delete() }
            Entity1.findAll().each { it.delete(flush: true) }
            final entity1 = new Entity1(field1: 'Correct').save(flush: true)
            new Entity1(field1: 'Incorrect').save(flush: true)
            new DetachedEntity(entityId: entity1.id, field: 'abc').save(flush: true)
            new DetachedEntity(entityId: entity1.id, field: 'def').save(flush: true)
            session.flush()
        }
    }

    @Rollback
    @Issue('https://github.com/apache/grails-data-mapping/issues/792')
    def 'closure projection fails'() {
        setup:
        final detachedCriteria = new DetachedCriteria(DetachedEntity).build {
            projections {
                distinct 'entityId'
            }
            eq 'field', 'abc'
        }
        when:
        // will fail
        def results = Entity1.withCriteria {
            inList 'id', detachedCriteria
        }
        then:
        results.size() == 1

    }

    @Rollback
    def 'closure projection manually'() {
        setup:
        final detachedCriteria = new DetachedCriteria(DetachedEntity).build {
            eq 'field', 'abc'
        }
        detachedCriteria.projections << new Query.DistinctPropertyProjection('entityId')
        expect:
        assert Entity1.withCriteria {
            inList 'id', detachedCriteria
        }.collect { it.field1 }.contains('Correct')
    }

    @Rollback
    def 'or fails in detached criteria'() {
        setup:
        final detachedCriteria = new DetachedCriteria(DetachedEntity).build {
            or {
                eq 'field', 'abc'
                eq 'field', 'def'
            }
        }
        detachedCriteria.projections << new Query.DistinctPropertyProjection('entityId')
        when:
        def results = Entity1.withCriteria {
            inList 'id', detachedCriteria
        }
        then:
        results.size() == 1
    }
}

@Entity
class Entity1 {
    Long id
    String field1
    static hasMany = [children : Entity2]
    static mapping = {
        version false
    }
}
@Entity
class Entity2 {
    static belongsTo = [parent: Entity1]
    String field
    static mapping = {
        version false
    }
}
@Entity
class DetachedEntity {
    Long id
    Long entityId
    String field
    static mapping = {
        version false
    }
}