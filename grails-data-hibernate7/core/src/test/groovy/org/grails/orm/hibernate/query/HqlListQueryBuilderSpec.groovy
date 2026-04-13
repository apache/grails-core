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

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.MappingCacheHolder
import org.grails.orm.hibernate.cfg.SortConfig
import spock.lang.Specification
import spock.lang.Unroll

class HqlListQueryBuilderSpec extends Specification {

    GrailsHibernatePersistentEntity entity = Mock(GrailsHibernatePersistentEntity)
    HibernateMappingContext mappingContext = Mock(HibernateMappingContext)
    MappingCacheHolder cacheHolder = Mock(MappingCacheHolder)

    def setup() {
        entity.getName() >> "Person"
        entity.getJavaClass() >> Object
        entity.getMappingContext() >> mappingContext
        mappingContext.getMappingCacheHolder() >> cacheHolder
    }

    void "test buildCountHql"() {
        given:
        def builder = new HqlListQueryBuilder(entity, [:])

        expect:
        builder.buildCountHql() == "select count(distinct e) from Person e"
    }

    void "test buildListHql with no arguments"() {
        given:
        def builder = new HqlListQueryBuilder(entity, [:])

        expect:
        builder.buildListHql() == "from Person e"
    }

    void "test buildListHql with simple sort"() {
        given:
        entity.getHibernatePropertyByName("name") >> Mock(HibernatePersistentProperty) {
            getType() >> String
        }
        def builder = new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.SORT.value()) : "name",
                (HibernateQueryArgument.ORDER.value()): HibernateQueryArgument.ORDER_DESC.value()
        ])

        expect:
        builder.buildListHql() == "from Person e order by upper(e.name) desc"
    }

    void "test buildListHql with numeric sort"() {
        given:
        entity.getHibernatePropertyByName("age") >> Mock(HibernatePersistentProperty) {
            getType() >> Integer
        }
        def builder = new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.SORT.value()) : "age",
                (HibernateQueryArgument.ORDER.value()): HibernateQueryArgument.ORDER_ASC.value()
        ])

        expect:
        builder.buildListHql() == "from Person e order by e.age asc"
    }

    void "test buildListHql with ignoreCase false"() {
        given:
        entity.getHibernatePropertyByName("name") >> Mock(HibernatePersistentProperty) {
            getType() >> String
        }
        def builder = new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.SORT.value())       : "name",
                (HibernateQueryArgument.IGNORE_CASE.value()): false
        ])

        expect:
        builder.buildListHql() == "from Person e order by e.name asc"
    }

    void "test buildListHql with multiple sorts"() {
        given:
        entity.getHibernatePropertyByName("name") >> Mock(HibernatePersistentProperty) { getType() >> String }
        entity.getHibernatePropertyByName("age") >> Mock(HibernatePersistentProperty) { getType() >> Integer }
        // Use LinkedHashMap to ensure deterministic order in HQL generation
        def builder = new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.SORT.value()): [
                        name: HibernateQueryArgument.ORDER_ASC.value(),
                        age : HibernateQueryArgument.ORDER_DESC.value()
                ]
        ])

        expect:
        builder.buildListHql() == "from Person e order by upper(e.name) asc, e.age desc"
    }

    void "test buildListHql with nested property sort"() {
        given:
        def authorAssociation = Mock(HibernatePersistentProperty)
        def authorEntity = Mock(GrailsHibernatePersistentEntity)
        entity.getHibernatePropertyByName("author") >> authorAssociation
        authorAssociation.getHibernateAssociatedEntity() >> authorEntity
        authorEntity.getHibernatePropertyByName("name") >> Mock(HibernatePersistentProperty) { getType() >> String }

        def builder = new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): "author.name"])

        expect:
        builder.buildListHql() == "from Person e order by upper(e.author.name) asc"
    }

    void "test buildListHql with join fetch"() {
        given:
        def builder = new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.FETCH.value()): [
                        books: HibernateQueryArgument.JOIN.value(),
                        profile: HibernateQueryArgument.EAGER.value()
                ]
        ])

        when:
        String hql = builder.buildListHql()

        then:
        hql.startsWith("from Person e")
        hql.contains(" join fetch e.books")
        hql.contains(" join fetch e.profile")
    }

    void "test buildListHql with default sort from mapping"() {
        given:
        def mapping = Mock(Mapping)
        def sortConfig = new SortConfig(name: "lastName", direction: "asc")

        cacheHolder.getMapping(_) >> mapping
        mapping.getSort() >> sortConfig
        entity.getHibernatePropertyByName("lastName") >> Mock(HibernatePersistentProperty) { getType() >> String }

        def builder = new HqlListQueryBuilder(entity, [:])

        expect:
        builder.buildListHql() == "from Person e order by upper(e.lastName) asc"
    }

    @Unroll
    void "test isPaged for params: #params"() {
        expect:
        HqlListQueryBuilder.isPaged(params) == expected

        where:
        params               | expected
        [:]                  | false
        [max: 10]            | true
        [offset: 5]          | true
        [max: 10, offset: 5] | true
    }
}
