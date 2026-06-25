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
package org.grails.orm.hibernate.query

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.datastore.mapping.query.Query

class HibernateHqlQueryCreatorSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(HqlCreatorSpecBook)
    }

    void "createHqlQuery returns SelectHqlQuery for SELECT"() {
        given:
        def entity = mappingContext.getPersistentEntity(HqlCreatorSpecBook.name)
        def ctx = HqlQueryContext.prepare(entity, "from HqlCreatorSpecBook", [:], null, [:], [:], false, false)


        when:
        def query = HibernateHqlQueryCreator.createHqlQuery(datastore, sessionFactory, entity, ctx)

        then:
        query instanceof SelectHqlQuery
    }

    void "createHqlQuery returns MutationHqlQuery for UPDATE"() {
        given:
        def entity = mappingContext.getPersistentEntity(HqlCreatorSpecBook.name)
        def ctx = HqlQueryContext.prepare(entity, "update HqlCreatorSpecBook set title = 'foo'", [:], null, [:], [:], false, true)

        when:
        def query = HibernateHqlQueryCreator.createHqlQuery(datastore, sessionFactory, entity, ctx)

        then:
        query instanceof MutationHqlQuery
    }

    void "createHqlQuery with native query"() {
        given:
        def entity = mappingContext.getPersistentEntity(HqlCreatorSpecBook.name)
        def ctx = HqlQueryContext.prepare(entity, "SELECT * FROM hql_creator_spec_book", [:], null, [:], [:], true, false)

        when:
        def query = HibernateHqlQueryCreator.createHqlQuery(datastore, sessionFactory, entity, ctx)

        then:
        query instanceof SelectHqlQuery
        query.queryContext.isNative()
    }
}

@Entity
class HqlCreatorSpecBook {
    String title
}
