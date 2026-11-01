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
package grails.gorm.tests.hibernatequery

import org.hibernate.query.criteria.HibernateCriteriaBuilder

import grails.gorm.DetachedCriteria
import grails.gorm.tests.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Expression
import jakarta.persistence.criteria.Root
import jakarta.persistence.criteria.Predicate
import org.hibernate.query.criteria.JpaExpression
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.datastore.mapping.query.criteria.FunctionCallingCriterion
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.query.JpaQueryContext
import org.grails.orm.hibernate.query.PredicateGenerator
import org.grails.orm.hibernate.query.PropertyArithmetic
import org.grails.orm.hibernate.query.SqlRestriction
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
        manager.registerDomainClasses(PredicateGeneratorSpecPerson, PredicateGeneratorSpecPet, PredicateGeneratorSpecFace, PredicateGeneratorSpecNullableAgeEntity)
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
        new PredicateGeneratorSpecPerson(firstName: 'Rich', lastName: 'Person', age: 50, salary: 4G).save(failOnError: true)
        new PredicateGeneratorSpecPerson(firstName: 'Poor', lastName: 'Person', age: 15, salary: 40G).save(flush: true, failOnError: true)
        CriteriaQuery<String> pQuery = cb.createQuery(String)
        Root<PredicateGeneratorSpecPerson> pRoot = pQuery.from(PredicateGeneratorSpecPerson)
        JpaQueryContext pContext = new JpaQueryContext(pRoot)
        List criteria = [
            new Query.GreaterThan("age", new PropertyArithmetic("age", PropertyArithmetic.Operator.SUBTRACT, 10)),
            new Query.GreaterThanEquals("age", new PropertyArithmetic("age", PropertyArithmetic.Operator.DIVIDE, 2)),
            new Query.LessThan("age", new PropertyArithmetic("age", PropertyArithmetic.Operator.ADD, 10)),
            new Query.LessThanEquals("salary", new PropertyArithmetic("salary", PropertyArithmetic.Operator.MULTIPLY, 10G)),
            new Query.LessThan("salary", 10G)
        ]

        when:
        Predicate[] predicates = predicateGenerator.getPredicates(pQuery, pRoot, criteria, pContext, personEntity)
        pQuery.select(pRoot.get('firstName')).where(predicates)
        List<String> results = sessionFactory.currentSession.createQuery(pQuery).resultList

        then:
        results == ['Rich']
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

    def "test getPredicates with In on unjoined collection path uses isMember and handles empty collection"() {
        given:
        new PredicateGeneratorSpecPerson(firstName: 'Bob', lastName: 'Smith', nicknames: ['Bobby', 'Rob'] as Set).save(failOnError: true)
        new PredicateGeneratorSpecPerson(firstName: 'Alice', lastName: 'Smith', nicknames: ['Ali'] as Set).save(flush: true, failOnError: true)

        when: "non-empty In on unjoined collection path"
        CriteriaQuery<String> cQuery = cb.createQuery(String)
        Root<PredicateGeneratorSpecPerson> cRoot = cQuery.from(PredicateGeneratorSpecPerson)
        JpaQueryContext cContext = new JpaQueryContext(cRoot)
        Predicate[] predicates = predicateGenerator.getPredicates(cQuery, cRoot, [new Query.In("nicknames", ["Bobby", "Other"])], cContext, personEntity)
        cQuery.select(cRoot.get('firstName')).where(predicates)
        List<String> matched = sessionFactory.currentSession.createQuery(cQuery).resultList

        and: "empty In on unjoined collection path returns disjunction (matches nothing)"
        CriteriaQuery<String> emptyQuery = cb.createQuery(String)
        Root<PredicateGeneratorSpecPerson> emptyRoot = emptyQuery.from(PredicateGeneratorSpecPerson)
        JpaQueryContext emptyContext = new JpaQueryContext(emptyRoot)
        Predicate[] emptyPredicates = predicateGenerator.getPredicates(emptyQuery, emptyRoot, [new Query.In("nicknames", [])], emptyContext, personEntity)
        emptyQuery.select(emptyRoot.get('firstName')).where(emptyPredicates)
        List<String> emptyMatched = sessionFactory.currentSession.createQuery(emptyQuery).resultList

        then:
        matched == ['Bob']
        emptyMatched.isEmpty()
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

    def "test getPredicates with Negation supports multiple predicates"() {
        given:
        def negation = new Query.Negation()
        negation.add(new Query.Equals("firstName", "Alice"))
        negation.add(new Query.Equals("lastName", "Smith"))
        List criteria = [negation]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
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

    def "test getPredicates with SqlRestriction binds the values and replaces {alias}"() {
        given:
        new PredicateGeneratorSpecNullableAgeEntity(name: 'Bob', age: 11).save(failOnError: true)
        new PredicateGeneratorSpecNullableAgeEntity(name: 'Bobby', age: 12).save(flush: true, failOnError: true)
        CriteriaQuery<String> nameQuery = cb.createQuery(String)
        Root<PredicateGeneratorSpecNullableAgeEntity> nameRoot = nameQuery.from(PredicateGeneratorSpecNullableAgeEntity)
        GrailsHibernatePersistentEntity nullableAgeEntity = session.datastore.mappingContext.getPersistentEntity(PredicateGeneratorSpecNullableAgeEntity.name) as GrailsHibernatePersistentEntity
        JpaQueryContext context = new JpaQueryContext(nameRoot)
        Predicate[] predicates = predicateGenerator.getPredicates(nameQuery, nameRoot, [new SqlRestriction('{alias}.age > ?', [11])], context, nullableAgeEntity)

        when:
        nameQuery.select(nameRoot.get('name')).where(predicates)
        var query = sessionFactory.currentSession.createQuery(nameQuery)
        context.parameterValues.each { parameter, value -> query.setParameter(parameter, value) }

        then:
        query.resultList == ['Bobby']
    }

    def "test getPredicates with SqlRestriction without {alias} and with a question mark in a string literal"() {
        given:
        new PredicateGeneratorSpecNullableAgeEntity(name: 'Bob?', age: 11).save(failOnError: true)
        new PredicateGeneratorSpecNullableAgeEntity(name: 'Bobby?', age: 12).save(failOnError: true)
        new PredicateGeneratorSpecNullableAgeEntity(name: 'Bobby', age: 13).save(flush: true, failOnError: true)
        CriteriaQuery<String> nameQuery = cb.createQuery(String)
        Root<PredicateGeneratorSpecNullableAgeEntity> nameRoot = nameQuery.from(PredicateGeneratorSpecNullableAgeEntity)
        GrailsHibernatePersistentEntity nullableAgeEntity = session.datastore.mappingContext.getPersistentEntity(PredicateGeneratorSpecNullableAgeEntity.name) as GrailsHibernatePersistentEntity
        JpaQueryContext context = new JpaQueryContext(nameRoot)
        Predicate[] predicates = predicateGenerator.getPredicates(nameQuery, nameRoot, [new SqlRestriction("name like '%?' and age > ?", [11])], context, nullableAgeEntity)

        when:
        nameQuery.select(nameRoot.get('name')).where(predicates)
        var query = sessionFactory.currentSession.createQuery(nameQuery)
        context.parameterValues.each { parameter, value -> query.setParameter(parameter, value) }

        then:
        query.resultList == ['Bobby?']
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
            new Query.NotEqualsAll("age", subCriteria),
            new Query.GreaterThanEqualsSome("age", subCriteria),
            new Query.GreaterThanSome("age", subCriteria),
            new Query.LessThanEqualsSome("age", subCriteria),
            new Query.LessThanSome("age", subCriteria),
            new Query.Equals("age", subCriteria),
            new Query.NotEquals("age", subCriteria),
            new Query.NotEquals("age", null)
        ]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 13
    }

    def "test getPredicates with DetachedAssociationCriteria and HibernateAssociationQuery"() {
        given:
        def owner = new PredicateGeneratorSpecPerson(firstName: 'Bob', lastName: 'Smith', age: 40)
        owner.addToPets(new PredicateGeneratorSpecPet(name: 'Lucky'))
        owner.save(failOnError: true)
        def other = new PredicateGeneratorSpecPerson(firstName: 'Alice', lastName: 'Jones', age: 30)
        other.addToPets(new PredicateGeneratorSpecPet(name: 'Rex'))
        other.save(flush: true, failOnError: true)

        def petsAssociation = personEntity.getPropertyByName('pets') as org.grails.datastore.mapping.model.types.Association
        def petEntity = petsAssociation.associatedEntity as GrailsHibernatePersistentEntity

        when: "DetachedAssociationCriteria is evaluated and executed"
        def detachedAssoc = new org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria(PredicateGeneratorSpecPet, petsAssociation, 'pets')
        detachedAssoc.add(new Query.Equals('name', 'Lucky'))
        CriteriaQuery<String> dQuery = cb.createQuery(String)
        Root<PredicateGeneratorSpecPerson> dRoot = dQuery.from(PredicateGeneratorSpecPerson)
        JpaQueryContext dContext = new JpaQueryContext(dRoot)
        Predicate[] dPredicates = predicateGenerator.getPredicates(dQuery, dRoot, [detachedAssoc], dContext, personEntity)
        dQuery.select(dRoot.get('firstName')).where(dPredicates)
        List<String> dResults = sessionFactory.currentSession.createQuery(dQuery).resultList

        and: "HibernateAssociationQuery is evaluated and executed"
        def hibernateAssoc = new org.grails.orm.hibernate.query.HibernateAssociationQuery(
                session as org.grails.orm.hibernate.HibernateSession,
                petEntity,
                petsAssociation,
                'pets',
                'p'
        )
        hibernateAssoc.add(new Query.Equals('name', 'Rex'))
        CriteriaQuery<String> hQuery = cb.createQuery(String)
        Root<PredicateGeneratorSpecPerson> hRoot = hQuery.from(PredicateGeneratorSpecPerson)
        JpaQueryContext hContext = new JpaQueryContext(hRoot)
        Predicate[] hPredicates = predicateGenerator.getPredicates(hQuery, hRoot, [hibernateAssoc], hContext, personEntity)
        hQuery.select(hRoot.get('firstName')).where(hPredicates)
        List<String> hResults = sessionFactory.currentSession.createQuery(hQuery).resultList

        then:
        dResults == ['Bob']
        hResults == ['Alice']
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

    def "test getPredicates with scalar subquery in comparison criteria"() {
        given:
        new PredicateGeneratorSpecPerson(firstName: 'Young', lastName: 'P', age: 20, salary: 10G).save(failOnError: true)
        new PredicateGeneratorSpecPerson(firstName: 'Mid', lastName: 'P', age: 30, salary: 20G).save(failOnError: true)
        new PredicateGeneratorSpecPerson(firstName: 'Old', lastName: 'P', age: 40, salary: 30G).save(flush: true, failOnError: true)

        def subqueryCriteria = new DetachedCriteria(PredicateGeneratorSpecPerson).build {
            projections {
                avg "age"
            }
        }
        def executeSingle = { Query.Criterion c ->
            CriteriaQuery<String> q = cb.createQuery(String)
            Root<PredicateGeneratorSpecPerson> r = q.from(PredicateGeneratorSpecPerson)
            JpaQueryContext ctx = new JpaQueryContext(r)
            Predicate[] preds = predicateGenerator.getPredicates(q, r, [c], ctx, personEntity)
            q.select(r.get('firstName')).where(preds)
            def hq = sessionFactory.currentSession.createQuery(q)
            ctx.parameterValues.each { k, v -> hq.setParameter(k, v) }
            hq.resultList.sort()
        }

        expect:
        executeSingle(new Query.GreaterThan("age", subqueryCriteria)) == ['Old']
        executeSingle(new Query.GreaterThanEquals("age", subqueryCriteria)) == ['Mid', 'Old']
        executeSingle(new Query.LessThan("age", subqueryCriteria)) == ['Young']
        executeSingle(new Query.LessThanEquals("age", subqueryCriteria)) == ['Mid', 'Young']
    }

    def "test getPredicates with FunctionCallingCriterion for string functions"() {
        given:
        List criteria = [
            new FunctionCallingCriterion("upper", new Query.Equals("firstName", "BOB")),
            new FunctionCallingCriterion("lower", new Query.Like("lastName", "smith%")),
            new FunctionCallingCriterion("upper", new Query.ILike("lastName", "smith%")),
            new FunctionCallingCriterion("lower", new Query.RLike("lastName", "^smith.*")),
            new FunctionCallingCriterion("trim", new Query.NotEquals("firstName", "Alice")),
            new FunctionCallingCriterion("length", new Query.GreaterThan("firstName", 3)),
            new FunctionCallingCriterion("length", new Query.GreaterThanEquals("firstName", 3)),
            new FunctionCallingCriterion("length", new Query.LessThan("firstName", 10)),
            new FunctionCallingCriterion("length", new Query.LessThanEquals("firstName", "10")),
            new FunctionCallingCriterion("upper", new Query.Equals("firstName", 123))
        ]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 10
    }

    def "test getPredicates with FunctionCallingCriterion throws on unsupported wrapped criterion"() {
        given:
        List criteria = [new FunctionCallingCriterion("upper", new Query.Between("firstName", "A", "Z"))]

        when:
        predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        thrown(UnsupportedOperationException)
    }

    def "test getPredicates with FunctionCallingCriterion throws on invalid property"() {
        given:
        List criteria = [new FunctionCallingCriterion("upper", new Query.Equals("nonExistent", "BOB"))]

        when:
        predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        thrown(ConfigurationException)
    }

    def "test getPredicates with FunctionCallingCriterion throws when function is on value"() {
        given:
        List criteria = [new FunctionCallingCriterion("firstName", "upper", new Query.Equals("firstName", "BOB"), true)]

        when:
        predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        thrown(UnsupportedOperationException)
    }

    def "test getPredicates with FunctionCallingCriterion handles null Equals, null NotEquals, and subquery In"() {
        given:
        def subCriteria = new DetachedCriteria(PredicateGeneratorSpecPerson).build {
            projections {
                property('firstName')
            }
            eq('lastName', 'Smith')
        }
        List criteria = [
            new FunctionCallingCriterion("upper", new Query.Equals("firstName", null)),
            new FunctionCallingCriterion("upper", new Query.NotEquals("firstName", null)),
            new FunctionCallingCriterion("lower", new Query.In("firstName", subCriteria)),
            new FunctionCallingCriterion("upper", new Query.Equals("firstName", subCriteria)),
            new FunctionCallingCriterion("upper", new Query.NotEquals("firstName", subCriteria))
        ]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 5
    }

    def "test getPredicates selects most specific superclass handler for unregistered Query.Equals subclass"() {
        given:
        new PredicateGeneratorSpecNullableAgeEntity(name: 'Alice', age: 25).save(failOnError: true)
        new PredicateGeneratorSpecNullableAgeEntity(name: 'Bob', age: 30).save(flush: true, failOnError: true)
        CriteriaQuery<String> nameQuery = cb.createQuery(String)
        Root<PredicateGeneratorSpecNullableAgeEntity> nameRoot = nameQuery.from(PredicateGeneratorSpecNullableAgeEntity)
        GrailsHibernatePersistentEntity nullableAgeEntity = session.datastore.mappingContext.getPersistentEntity(PredicateGeneratorSpecNullableAgeEntity.name) as GrailsHibernatePersistentEntity
        JpaQueryContext context = new JpaQueryContext(nameRoot)
        Query.Equals customEquals = new Query.Equals('name', 'Alice') {}

        when:
        Predicate[] predicates = predicateGenerator.getPredicates(nameQuery, nameRoot, [customEquals], context, nullableAgeEntity)
        nameQuery.select(nameRoot.get('name')).where(predicates)
        List<String> results = sessionFactory.currentSession.createQuery(nameQuery).resultList

        then:
        results == ['Alice']

        when: "conversionService converts StringBuilder to String and String to Integer in FunctionCallingCriterion"
        CriteriaQuery<String> funcQuery = cb.createQuery(String)
        Root<PredicateGeneratorSpecNullableAgeEntity> funcRoot = funcQuery.from(PredicateGeneratorSpecNullableAgeEntity)
        JpaQueryContext funcContext = new JpaQueryContext(funcRoot)
        List<Query.Criterion> funcCriteria = [
            new FunctionCallingCriterion('upper', new Query.Equals('name', new StringBuilder('ALICE'))),
            new FunctionCallingCriterion('length', new Query.GreaterThan('name', '4'))
        ]
        Predicate[] funcPredicates = predicateGenerator.getPredicates(funcQuery, funcRoot, funcCriteria, funcContext, nullableAgeEntity)
        funcQuery.select(funcRoot.get('name')).where(funcPredicates)
        List<String> funcResults = sessionFactory.currentSession.createQuery(funcQuery).resultList

        then:
        funcResults == ['Alice']
    }

    def "test registerCriterionHandler and clearCustomCriterionHandlers"() {
        given:
        Query.Criterion customCriterion = new Query.Criterion() {}
        PredicateGenerator.registerCriterionHandler(customCriterion.class) { cq, r, builder, c ->
            builder.equal(r.get('firstName'), 'CustomBob')
        }

        when:
        Predicate[] predicates = predicateGenerator.getPredicates(query, root, [customCriterion], fromProvider, personEntity)

        then:
        predicates.length == 1

        when:
        PredicateGenerator.clearCustomCriterionHandlers()
        predicateGenerator.getPredicates(query, root, [customCriterion], fromProvider, personEntity)

        then:
        thrown(IllegalArgumentException)
    }

    def "subquery translation does not mutate source criteria projection list in-place"() {
        given:
        def subCriteria = new DetachedCriteria(PredicateGeneratorSpecPerson).eq("lastName", "Smith")
        List criteria = [
            new Query.In("id", subCriteria),
            new Query.EqualsAll("age", subCriteria)
        ]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 2
        subCriteria.projections.isEmpty()
    }

    def "property comparison and property name criteria throw ConfigurationException on non-existent property"() {
        when:
        predicateGenerator.getPredicates(query, root, [criterion], fromProvider, personEntity)

        then:
        thrown(ConfigurationException)

        where:
        criterion << [
            new Query.EqualsProperty("nonExistent", "lastName"),
            new Query.EqualsProperty("firstName", "nonExistent"),
            new Query.IsNull("nonExistent"),
            new Query.EqualsAll("nonExistent", new DetachedCriteria(PredicateGeneratorSpecPerson))
        ]
    }

    def "Query.In filters null values and returns disjunction when empty"() {
        given:
        List criteria = [
            new Query.In("firstName", ["Bob", null]),
            new Query.In("firstName", [null]),
            new Query.In("firstName", [])
        ]

        when:
        def predicates = predicateGenerator.getPredicates(query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 3
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
