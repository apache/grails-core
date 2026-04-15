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
package grails.gorm.specs.hibernatequery

import org.hibernate.query.criteria.HibernateCriteriaBuilder

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Root
import jakarta.persistence.criteria.Predicate
import org.hibernate.query.criteria.JpaExpression
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.query.JpaQueryContext
import org.grails.orm.hibernate.query.PredicateGenerator
import org.grails.orm.hibernate.query.PropertyArithmetic
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

class PredicateGeneratorSpec extends HibernateGormDatastoreSpec {

    PredicateGenerator predicateGenerator
    HibernateCriteriaBuilder cb
    CriteriaQuery query
    Root root
    JpaQueryContext fromProvider
    GrailsHibernatePersistentEntity personEntity

    void setupSpec() {
        manager.addAllDomainClasses([PredicateGeneratorSpecPerson, PredicateGeneratorSpecPet, PredicateGeneratorSpecFace, PredicateGeneratorSpecNullableAgeEntity])
    }

    void setup() {
        cb = sessionFactory.getCriteriaBuilder()
        query = cb.createQuery(PredicateGeneratorSpecPerson)
        root = query.from(PredicateGeneratorSpecPerson)
        personEntity = session.datastore.mappingContext.getPersistentEntity(PredicateGeneratorSpecPerson.name) as GrailsHibernatePersistentEntity
        fromProvider = new JpaQueryContext(root)
        predicateGenerator = new PredicateGenerator(cb, session.datastore.mappingContext.conversionService)
    }

    def "test getPredicates with Equals criterion"() {
        given:
        List criteria = [new Query.Equals("firstName", "Bob")]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Between criterion"() {
        given:
        List criteria = [new Query.Between("age", 20, 30)]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with In criterion"() {
        given:
        List criteria = [new Query.In("firstName", ["Bob", "Alice"])]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Conjunction"() {
        given:
        List criteria = [new Query.Conjunction()
                                 .add(new Query.Equals("firstName", "Bob"))
                                 .add(new Query.Equals("lastName", "Smith"))]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Exists"() {
        given:
        List criteria = [new Query.Exists(new DetachedCriteria(PredicateGeneratorSpecPet).eq("name", "Lucky"))]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with subquery isolated provider"() {
        given: "a subquery with association reference"
        def subCriteria = new DetachedCriteria(PredicateGeneratorSpecPet).eq("face.name", "Funny")
        List criteria = [new Query.In("id", subCriteria)]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then: "no exception thrown during subquery join creation"
        noExceptionThrown()
        predicates.length == 1
    }

    def "test getPredicates with subquery aliases"() {
        given: "a subquery with an alias"
        def subCriteria = new DetachedCriteria(PredicateGeneratorSpecPet).build {
            createAlias('face', 'f')
            eq('f.name', 'Funny')
        }
        List criteria = [new Query.In("id", subCriteria)]
        
        // Register the expected join response for face
        def faceJoin = Mock(jakarta.persistence.criteria.Join)
        def namePath = Mock(jakarta.persistence.criteria.Path)
        // Note: In real scenarios creator would join, in this spec we are testing PredicateGenerator's ability 
        // to handle the subquery traversal.

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then: "the alias 'f' is correctly resolved"
        noExceptionThrown()
        predicates.length == 1
    }

    def "test getPredicates with Disjunction"() {
        given:
        List criteria = [new Query.Disjunction()
                                 .add(new Query.Equals("firstName", "Bob"))
                                 .add(new Query.Equals("firstName", "Alice"))]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Negation"() {
        given:
        List criteria = [new Query.Negation().add(new Query.Equals("firstName", "Bob"))]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Property Comparison"() {
        given:
        List criteria = [new Query.EqualsProperty("firstName", "lastName")]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Like and ILike"() {
        given:
        List criteria = [
            new Query.Like("firstName", "B%"),
            new Query.ILike("firstName", "b%")
        ]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 2
    }

    def "test getPredicates with Size Comparison"() {
        given:
        List criteria = [new Query.SizeEquals("pets", 2)]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "getPredicates supports PropertyArithmetic on RHS of GreaterThan (age > salary * 10)"() {
        given:
        List criteria = [new Query.GreaterThan("age", new PropertyArithmetic("salary", PropertyArithmetic.Operator.MULTIPLY, 10))]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with In on basic collection"() {
        given:
        List criteria = [new Query.In("nicknames", ["Bob", "Alice"])]
        
        // Ensure nicknames is joined in fromProvider
        fromProvider = new JpaQueryContext(root)
        fromProvider.addFrom("nicknames", root.join("nicknames"))

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
        predicates[0] instanceof org.hibernate.query.sqm.tree.predicate.SqmInListPredicate
    }

    def "test getPredicates with DistinctProjection returns conjunction"() {
        given:
        List criteria = [new Query.DistinctProjection()]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with NotExists criterion"() {
        given:
        List criteria = [new Query.NotExists(new DetachedCriteria(PredicateGeneratorSpecPet).eq("name", "Lucky"))]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with IsNull and IsNotNull criteria"() {
        given:
        List criteria = [
            new Query.IsNull("firstName"),
            new Query.IsNotNull("lastName")
        ]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 2
    }

    def "test getPredicates with IsEmpty and IsNotEmpty criteria"() {
        given:
        List criteria = [
            new Query.IsEmpty("pets"),
            new Query.IsNotEmpty("pets")
        ]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 2
    }

    def "test getPredicates with NotEqualsProperty comparison"() {
        given:
        List criteria = [new Query.NotEqualsProperty("firstName", "lastName")]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThanProperty and GreaterThanProperty comparisons"() {
        given:
        List criteria = [
            new Query.LessThanProperty("age", "age"),
            new Query.GreaterThanProperty("age", "age"),
            new Query.LessThanEqualsProperty("age", "age"),
            new Query.GreaterThanEqualsProperty("age", "age")
        ]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 4
    }

    def "test getPredicates throws for unsupported criterion"() {
        given:
        def unsupportedCriterion = new Query.Criterion() {} // anonymous implementation
        List<Query.QueryElement> criteria = [unsupportedCriterion]

        when:
        predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        thrown(IllegalArgumentException)
    }

    def "test getPredicates with HibernateAlias returns null (metadata only)"() {
        given:
        def alias = new org.grails.orm.hibernate.query.HibernateAlias("pets", "p")
        List criteria = [alias, new Query.Equals("firstName", "Bob")]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Negation throws when multiple predicates"() {
        given:
        def negation = new Query.Negation()
        negation.add(new Query.Equals("firstName", "Alice"))
        negation.add(new Query.Equals("lastName", "Smith"))
        List criteria = [negation]

        when:
        predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        thrown(RuntimeException)
    }

    def "test getPredicates with invalid property throws ConfigurationException"() {
        given:
        List criteria = [new Query.Equals("nonExistentProperty", "value")]

        when:
        predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        thrown(Exception)
    }

    def "test getPredicates with NotEquals criterion"() {
        given:
        List criteria = [new Query.NotEquals("firstName", "Bob")]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with NotEquals criterion includes null values"() {
        given:
        new PredicateGeneratorSpecNullableAgeEntity(name: "Null 1", age: null).save(failOnError: true)
        new PredicateGeneratorSpecNullableAgeEntity(name: "Equal", age: 11).save(failOnError: true)
        new PredicateGeneratorSpecNullableAgeEntity(name: "Null 2", age: null).save(flush: true, failOnError: true)
        CriteriaQuery<Long> countQuery = cb.createQuery(Long)
        Root<PredicateGeneratorSpecNullableAgeEntity> countRoot = countQuery.from(PredicateGeneratorSpecNullableAgeEntity)
        GrailsHibernatePersistentEntity nullableAgeEntity = session.datastore.mappingContext.getPersistentEntity(PredicateGeneratorSpecNullableAgeEntity.name) as GrailsHibernatePersistentEntity
        JpaQueryContext countFromProvider = new JpaQueryContext(countRoot)
        Predicate[] predicates = predicateGenerator.getPredicates(countQuery, countRoot, [new Query.NotEquals("age", 11)], countFromProvider, nullableAgeEntity)

        when:
        countQuery.select(cb.count(countRoot)).where(predicates)
        Long count = sessionFactory.currentSession.createQuery(countQuery).singleResult

        then:
        count == 2L
    }

    def "test getPredicates with IdEquals criterion"() {
        given:
        List criteria = [new Query.IdEquals(1L)]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with GreaterThan and LessThan numeric criteria"() {
        given:
        List criteria = [
            new Query.GreaterThan("age", 18),
            new Query.GreaterThanEquals("age", 18),
            new Query.LessThan("age", 65),
            new Query.LessThanEquals("age", 65)
        ]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 4
    }

    def "test getPredicates with GreaterThan and null value throws ConfigurationException"() {
        given:
        List criteria = [new Query.GreaterThan("age", null)]

        when:
        predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        thrown(Exception)
    }

    def "test getPredicates with normalizeValue for CharSequence"() {
        given:
        def sb = new StringBuilder("Bob")
        List criteria = [new Query.Equals("firstName", sb)]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with RLike criterion"() {
        given:
        List criteria = [new Query.RLike("firstName", "^B.*")]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with NotIn criterion"() {
        given:
        def subCriteria = new DetachedCriteria(PredicateGeneratorSpecPerson).eq("lastName", "Smith")
        List criteria = [new Query.NotIn("id", subCriteria)]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with all subquery criteria"() {
        given:
        def subCriteria = new DetachedCriteria(PredicateGeneratorSpecPerson).eq("lastName", "Smith")
        List criteria = [
            new Query.GreaterThanEqualsAll("age", subCriteria),
            new Query.GreaterThanAll("age", subCriteria),
            new Query.LessThanEqualsAll("age", subCriteria),
            new Query.LessThanAll("age", subCriteria),
            new Query.EqualsAll("age", subCriteria),
            new Query.GreaterThanEqualsSome("age", subCriteria),
            new Query.GreaterThanSome("age", subCriteria),
            new Query.LessThanEqualsSome("age", subCriteria),
            new Query.LessThanSome("age", subCriteria)
        ]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 9
    }

    def "test getPredicates with all Size criteria"() {
        given:
        List criteria = [
            new Query.SizeEquals("pets", 2),
            new Query.SizeNotEquals("pets", 3),
            new Query.SizeGreaterThan("pets", 1),
            new Query.SizeGreaterThanEquals("pets", 1),
            new Query.SizeLessThan("pets", 5),
            new Query.SizeLessThanEquals("pets", 5)
        ]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 6
    }

    def "getPredicates supports Subquery"() {
        given:
        jakarta.persistence.criteria.Subquery subquery = query.subquery(Long)
        subquery.from(PredicateGeneratorSpecPerson)
        List criteria = [new Query.Equals("firstName", "Bob")]

        when:
        def predicates = predicateGenerator.getPredicates(subquery, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "handlePropertyCriterion resolves aliased expression"() {
        given:
        Expression aliasedExpr = root.get("firstName")
        fromProvider.registerAlias("myAlias", aliasedExpr)
        List criteria = [new Query.Equals("myAlias", "Bob")]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }
}

@Entity
class PredicateGeneratorSpecPerson implements GormEntity<PredicateGeneratorSpecPerson> {
    Long id
    String firstName
    String lastName
    Integer age
    BigDecimal salary
    PredicateGeneratorSpecFace face
    Set<String> nicknames
    static hasMany = [pets: PredicateGeneratorSpecPet, nicknames: String]
}

@Entity
class PredicateGeneratorSpecPet implements GormEntity<PredicateGeneratorSpecPet> {
    Long id
    String name
    PredicateGeneratorSpecFace face
    static belongsTo = [owner: PredicateGeneratorSpecPerson]
}

@Entity
class PredicateGeneratorSpecFace implements GormEntity<PredicateGeneratorSpecFace> {
    Long id
    String name
}

@Entity
class PredicateGeneratorSpecNullableAgeEntity implements GormEntity<PredicateGeneratorSpecNullableAgeEntity> {
    Long id
    String name
    Integer age

    static constraints = {
        age nullable: true
    }
}
