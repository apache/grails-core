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
        def prop = Mock(HibernatePersistentProperty)
        prop.getType() >> String
        entity.getHibernatePropertyByPath("name") >> prop
        
        def builder = new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.SORT.value()) : "name",
                (HibernateQueryArgument.ORDER.value()): HibernateQueryArgument.ORDER_DESC.value()
        ])

        expect:
        builder.buildListHql() == "from Person e order by upper(e.name) desc"
    }

    void "test buildListHql with numeric sort"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        prop.getType() >> Integer
        entity.getHibernatePropertyByPath("age") >> prop

        def builder = new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.SORT.value()) : "age",
                (HibernateQueryArgument.ORDER.value()): HibernateQueryArgument.ORDER_ASC.value()
        ])

        expect:
        builder.buildListHql() == "from Person e order by e.age asc"
    }

    void "test buildListHql with ignoreCase false"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        prop.getType() >> String
        entity.getHibernatePropertyByPath("name") >> prop

        def builder = new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.SORT.value())       : "name",
                (HibernateQueryArgument.IGNORE_CASE.value()): false
        ])

        expect:
        builder.buildListHql() == "from Person e order by e.name asc"
    }

    void "test buildListHql with multiple sorts"() {
        given:
        def nameProp = Mock(HibernatePersistentProperty)
        nameProp.getType() >> String
        def ageProp = Mock(HibernatePersistentProperty)
        ageProp.getType() >> Integer
        
        entity.getHibernatePropertyByPath("name") >> nameProp
        entity.getHibernatePropertyByPath("age") >> ageProp

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
        def prop = Mock(HibernatePersistentProperty)
        prop.getType() >> String
        entity.getHibernatePropertyByPath("author.name") >> prop

        def builder = new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): "author.name"])

        expect:
        builder.buildListHql() == "from Person e order by upper(e.author.name) asc"
    }

    void "test buildListHql with join fetch"() {
        given:
        entity.getHibernatePropertyByPath("books") >> Mock(HibernatePersistentProperty)
        entity.getHibernatePropertyByPath("profile") >> Mock(HibernatePersistentProperty)
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
        
        def prop = Mock(HibernatePersistentProperty)
        prop.getType() >> String
        entity.getHibernatePropertyByPath("lastName") >> prop

        def builder = new HqlListQueryBuilder(entity, [:])

        expect:
        builder.buildListHql() == "from Person e order by upper(e.lastName) asc"
    }

    void "test buildListHql with multiple default sorts from mapping"() {
        given:
        def mapping = Mock(Mapping)
        // Use LinkedHashMap to ensure deterministic order in HQL generation
        def namesAndDirections = [lastName: "asc", firstName: "desc"]
        def sortConfig = Mock(SortConfig)
        sortConfig.getNamesAndDirections() >> namesAndDirections

        cacheHolder.getMapping(_) >> mapping
        mapping.getSort() >> sortConfig
        
        def lastProp = Mock(HibernatePersistentProperty)
        lastProp.getType() >> String
        def firstProp = Mock(HibernatePersistentProperty)
        firstProp.getType() >> String
        
        entity.getHibernatePropertyByPath("lastName") >> lastProp
        entity.getHibernatePropertyByPath("firstName") >> firstProp

        def builder = new HqlListQueryBuilder(entity, [:])

        expect:
        builder.buildListHql() == "from Person e order by upper(e.lastName) asc, upper(e.firstName) desc"
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

    @Unroll
    void "test buildListHql rejects sort property #description without echoing it"() {
        when:
        new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): sort]).buildListHql()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid sort property"

        where:
        sort           | description
        "name, e.id"   | "carrying a second expression"
        "name desc"    | "carrying a direction"
        "upper(name)"  | "wrapped in a function call"
        "notAProperty" | "that the mapping does not know"
        ""             | "that is empty"
        " "            | "that is blank"
    }

    void "test buildListHql rejects a blank property in a sort map instead of emitting an empty order part"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        prop.getType() >> String
        entity.getHibernatePropertyByPath("name") >> prop

        when:
        new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): ["": "asc", name: "asc"]]).buildListHql()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid sort property"
    }

    void "test buildListHql accepts identifier characters beyond ASCII letters in a sort property"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        prop.getType() >> Integer
        entity.getHibernatePropertyByPath('total$amount') >> prop

        expect:
        new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): 'total$amount']).buildListHql() ==
                'from Person e order by e.total$amount asc'
    }

    void "test buildListHql rejects injected sort direction without echoing it"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        prop.getType() >> String
        entity.getHibernatePropertyByPath("name") >> prop

        when:
        new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.SORT.value()) : "name",
                (HibernateQueryArgument.ORDER.value()): "desc; delete"
        ]).buildListHql()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid sort direction"
    }

    @Unroll
    void "test buildListHql normalizes sort direction '#direction' to #expected"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        prop.getType() >> Integer
        entity.getHibernatePropertyByPath("age") >> prop

        expect:
        new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.SORT.value()) : "age",
                (HibernateQueryArgument.ORDER.value()): direction
        ]).buildListHql() == "from Person e order by e.age ${expected}"

        where:
        direction | expected
        " desc"   | "desc"
        "DESC "   | "desc"
        "Asc"     | "asc"
        ""        | "asc"
        "   "     | "asc"
    }

    void "test buildListHql validates the order argument even when there is no sort key"() {
        when:
        new HqlListQueryBuilder(entity, [(HibernateQueryArgument.ORDER.value()): "sideways"]).buildListHql()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid sort direction"

        when:
        def hql = new HqlListQueryBuilder(entity, [(HibernateQueryArgument.ORDER.value()): "desc"]).buildListHql()

        then:
        hql == "from Person e"
    }

    @Unroll
    void "test buildListHql rejects fetch key #description without echoing it"() {
        when:
        new HqlListQueryBuilder(entity, [(HibernateQueryArgument.FETCH.value()): [(key): HibernateQueryArgument.JOIN.value()]]).buildListHql()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid fetch property"

        where:
        key                                   | description
        "books left join fetch e.secretNotes" | "carrying a second join"
        "books, e.secretNotes"                | "carrying a second expression"
        "notAnAssociation"                    | "that the mapping does not know"
        ""                                    | "that is empty"
    }

    void "test buildListHql ignores fetch keys whose value is neither join nor eager"() {
        given:
        def builder = new HqlListQueryBuilder(entity, [(HibernateQueryArgument.FETCH.value()): [books: "select"]])

        expect:
        builder.buildListHql() == "from Person e"
    }
}
