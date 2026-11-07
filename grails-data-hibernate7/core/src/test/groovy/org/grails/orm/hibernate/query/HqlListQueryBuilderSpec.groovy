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

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
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

    /** Stubs an association of the owner and returns the entity that it refers to. */
    private PersistentEntity associated(PersistentEntity owner, String name, Association kind = null) {
        Association association = kind ?: Mock(Association)
        def associatedEntity = Mock(PersistentEntity)
        association.getAssociatedEntity() >> associatedEntity
        owner.getPropertyByName(name) >> association
        associatedEntity
    }

    /** Stubs a property of the owner that has the given type. */
    private void property(PersistentEntity owner, String name, Class type) {
        def prop = Mock(HibernatePersistentProperty)
        prop.getType() >> type
        owner.getPropertyByName(name) >> prop
    }

    private HibernateManyToOneProperty manyToOne() {
        def association = Mock(HibernateManyToOneProperty)
        association.isValidHibernateManyToOne() >> true
        association
    }

    private PersistentProperty identity(String name = "id") {
        def identity = Mock(PersistentProperty)
        identity.getName() >> name
        identity
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
        entity.getPropertyByName("name") >> prop
        
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
        entity.getPropertyByName("age") >> prop

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
        entity.getPropertyByName("name") >> prop

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
        
        entity.getPropertyByName("name") >> nameProp
        entity.getPropertyByName("age") >> ageProp

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

    void "test buildListHql rejects a sort path through a property that is not an association"() {
        given:
        property(entity, "name", String)

        when:
        new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): "name.length"]).buildListHql()

        then: "the one walk of the path finds nothing to go on from"
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid sort property"
    }

    void "test buildListHql rejects a sort path that ends with a property the associated entity does not have"() {
        given:
        associated(entity, "author")

        when:
        new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): "author.unknown"]).buildListHql()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid sort property"
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
        entity.getPropertyByName("lastName") >> prop

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
        
        entity.getPropertyByName("lastName") >> lastProp
        entity.getPropertyByName("firstName") >> firstProp

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
        entity.getPropertyByName("name") >> prop

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
        entity.getPropertyByName('total$amount') >> prop

        expect:
        new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): 'total$amount']).buildListHql() ==
                'from Person e order by e.total$amount asc'
    }

    void "test buildListHql rejects injected sort direction without echoing it"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        prop.getType() >> String
        entity.getPropertyByName("name") >> prop

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
        entity.getPropertyByName("age") >> prop

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

    void "test buildListHql sorts by a property of an association through a left join"() {
        given:
        property(associated(entity, "author"), "name", String)

        def builder = new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): "author.name"])

        expect: "the path is not left to Hibernate, which would join the association with an inner join"
        builder.buildListHql() == "from Person e left join e.author s0 order by upper(s0.name) asc"
    }

    void "test buildListHql joins every association of a nested sort path"() {
        given:
        def authorEntity = associated(entity, "author")
        property(associated(authorEntity, "publisher"), "id", Long)

        def builder = new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): "author.publisher.id"])

        expect:
        builder.buildListHql() == "from Person e left join e.author s0 left join s0.publisher s1 order by s1.id asc"
    }

    void "test buildListHql sorts by the identifier of a many-to-one association without a join"() {
        given:
        def authorEntity = associated(entity, "author", manyToOne())
        authorEntity.getIdentity() >> identity()
        property(authorEntity, "id", Long)

        def builder = new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): "author.id"])

        expect: "the identifier is the foreign key, a column of the entity, which keeps the rows without an author"
        builder.buildListHql() == "from Person e order by e.author.id asc"
    }

    void "test buildListHql joins only the associations before the identifier of a nested many-to-one association"() {
        given:
        def authorEntity = associated(entity, "author", manyToOne())
        authorEntity.getIdentity() >> identity()
        def publisherEntity = associated(authorEntity, "publisher", manyToOne())
        publisherEntity.getIdentity() >> identity()
        property(publisherEntity, "id", Long)

        def builder = new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): "author.publisher.id"])

        expect:
        builder.buildListHql() == "from Person e left join e.author s0 order by s0.publisher.id asc"
    }

    void "test buildListHql joins an association whose foreign key is not in the table of the entity for its identifier"() {
        given:
        def passport = Mock(HibernateOneToOneProperty)
        passport.isValidHibernateManyToOne() >> false
        def passportEntity = associated(entity, "passport", passport)
        passportEntity.getIdentity() >> identity()
        property(passportEntity, "id", Long)

        def builder = new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): "passport.id"])

        expect: "the identifier is read from the table of the passport, which a hasOne association has its key in"
        builder.buildListHql() == "from Person e left join e.passport s0 order by s0.id asc"
    }

    void "test buildListHql joins a many-to-one association for a property other than its identifier"() {
        given:
        def authorEntity = associated(entity, "author", manyToOne())
        authorEntity.getIdentity() >> identity()
        property(authorEntity, "age", Long)

        def builder = new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): "author.age"])

        expect:
        builder.buildListHql() == "from Person e left join e.author s0 order by s0.age asc"
    }

    void "test buildListHql joins an association once for two sort properties"() {
        given:
        def authorEntity = associated(entity, "author")
        property(authorEntity, "name", String)
        property(authorEntity, "age", Integer)

        def builder = new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.SORT.value()): ["author.name": "asc", "author.age": "desc"]
        ])

        expect:
        builder.buildListHql() == "from Person e left join e.author s0 order by upper(s0.name) asc, s0.age desc"
    }

    void "test buildListHql sorts through the fetch join of the same association"() {
        given:
        entity.getHibernatePropertyByPath("author") >> Mock(HibernatePersistentProperty)
        entity.getHibernatePropertyByPath("books") >> Mock(HibernatePersistentProperty)
        property(associated(entity, "author"), "name", String)

        def builder = new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.FETCH.value()): [books: HibernateQueryArgument.JOIN.value(), author: HibernateQueryArgument.JOIN.value()],
                (HibernateQueryArgument.SORT.value()) : "author.name"
        ])

        expect: "the association is not joined a second time, and a fetch that is not sorted through has no alias"
        builder.buildListHql() == "from Person e join fetch e.books join fetch e.author f0 order by upper(f0.name) asc"
    }

    void "test buildListHql joins the associations after a fetch join from the alias of the fetch"() {
        given:
        entity.getHibernatePropertyByPath("author") >> Mock(HibernatePersistentProperty)
        def authorEntity = associated(entity, "author")
        property(associated(authorEntity, "publisher"), "name", String)

        def builder = new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.FETCH.value()): [author: HibernateQueryArgument.EAGER.value()],
                (HibernateQueryArgument.SORT.value()) : ["author.publisher.name": "desc"]
        ])

        expect:
        builder.buildListHql() == "from Person e join fetch e.author f0 left join f0.publisher s0 order by upper(s0.name) desc"
    }

    void "test buildListHql keeps the sort join of an association that is not fetched"() {
        given:
        entity.getHibernatePropertyByPath("books") >> Mock(HibernatePersistentProperty)
        property(associated(entity, "author"), "name", String)

        def builder = new HqlListQueryBuilder(entity, [
                (HibernateQueryArgument.FETCH.value()): [books: HibernateQueryArgument.JOIN.value()],
                (HibernateQueryArgument.SORT.value()) : "author.name"
        ])

        expect:
        builder.buildListHql() == "from Person e join fetch e.books left join e.author s0 order by upper(s0.name) asc"
    }

    void "test buildListHql sorts by a property of an embedded component without a join"() {
        given:
        property(associated(entity, "address", Mock(Embedded)), "city", String)

        def builder = new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): "address.city"])

        expect:
        builder.buildListHql() == "from Person e order by upper(e.address.city) asc"
    }

    void "test buildListHql sorts by a property of an association inside an embedded component through a left join"() {
        given:
        def detailsEntity = associated(entity, "details", Mock(Embedded))
        property(associated(detailsEntity, "author"), "name", String)

        def builder = new HqlListQueryBuilder(entity, [(HibernateQueryArgument.SORT.value()): "details.author.name"])

        expect: "the component has no join of its own, and the association is joined through its path"
        builder.buildListHql() == "from Person e left join e.details.author s0 order by upper(s0.name) asc"
    }
}
