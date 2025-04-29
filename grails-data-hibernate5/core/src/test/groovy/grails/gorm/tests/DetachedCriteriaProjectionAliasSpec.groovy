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
import grails.gorm.transactions.Rollback
import grails.gorm.transactions.Transactional
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification


class DetachedCriteriaProjectionAliasSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(Entity1, Entity2, DetachedEntity)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    @Transactional
    def setup() {
        DetachedEntity.findAll().each { it.delete() }
        Entity1.findAll().each { it.delete(flush: true) }
        Entity2.findAll().each { it.delete(flush: true) }
        final entity1 = new Entity1(id: 1, field1: 'E1').save()
        final entity2 = new Entity2(id: 2, field: 'E2', parent: entity1).save()
        entity1.addToChildren(entity2)
        new DetachedEntity(id: 1, entityId: entity1.id, field: 'DE1').save()
        new DetachedEntity(id: 2, entityId: entity1.id, field: 'DE2').save()
    }

    @Rollback
    @Issue('https://github.com/grails/gorm-hibernate5/issues/598')
    def 'test projection in detached criteria subquery with aliased join and restriction referencing join'() {
        setup:
        final detachedCriteria = new DetachedCriteria(Entity1).build {
            createAlias("children", "e2")
            projections{
                property("id")
            }
            eq("e2.field", "E2")
        }
        when:
        def res = DetachedEntity.withCriteria {
            "in"("entityId", detachedCriteria)
        }
        then:
        res.entityId.first() == 1L
    }


    @Rollback
    @Issue('https://github.com/grails/gorm-hibernate5/issues/598')
    def 'test aliased projection in detached criteria subquery'() {
        setup:
        final detachedCriteria = new DetachedCriteria(Entity2).build {
            createAlias("parent", "e1")
            projections{
                property("e1.id")
            }
            eq("field", "E2")
        }
        when:
        def res = DetachedEntity.withCriteria {
            "in"("entityId", detachedCriteria)
        }
        then:
        res.entityId.first() == 2L
    }
}