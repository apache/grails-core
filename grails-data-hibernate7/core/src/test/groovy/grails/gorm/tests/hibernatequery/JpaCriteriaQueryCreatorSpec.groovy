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
import spock.lang.Shared

import grails.gorm.DetachedCriteria
import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.gorm.tests.UserTypeComparisonGrade
import grails.gorm.tests.UserTypeComparisonGradeType
import org.grails.datastore.mapping.query.Query
import org.hibernate.query.criteria.JpaCriteriaQuery
import org.grails.orm.hibernate.query.JpaCriteriaQueryCreator
import org.grails.orm.hibernate.query.JpaQueryContext
import org.grails.orm.hibernate.query.SqlGroupProjection
import org.grails.orm.hibernate.query.SqlProjection
import org.springframework.core.convert.support.DefaultConversionService
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

class JpaCriteriaQueryCreatorSpec extends HibernateGormDatastoreSpec {


    void setupSpec() {
        manager.registerDomainClasses(JpaCriteriaQueryCreatorSpecPerson, JpaCriteriaQueryCreatorSpecPet, JpaCriteriaQueryCreatorSpecGraded)
    }

    void 'alias projections retain the requested outer join'() {
        given:
        def owner = new JpaCriteriaQueryCreatorSpecPerson(firstName: 'Alice', lastName: 'Owner')
                .save(failOnError: true)
        new JpaCriteriaQueryCreatorSpecPerson(firstName: 'Bob', lastName: 'WithoutPet')
                .save(failOnError: true)
        new JpaCriteriaQueryCreatorSpecPet(name: 'pet', owner: owner)
                .save(flush: true, failOnError: true)

        expect:
        JpaCriteriaQueryCreatorSpecPet.createCriteria().list {
            createAlias('owner', 'o', 2)
            projections { property('o.firstName') }
            order('o.firstName', 'asc')
        } == ['Alice', 'Bob']
    }

    def "test createQuery"() {
        given:
       
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson.name)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
    }

    def "test createQuery with projections"() {
        given:
      
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson.name)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        
        var projections = new Query.ProjectionList()
        projections.property("firstName")
        projections.property("lastName")

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
    }

    def "test createQuery with distinct"() {
        given:
      
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson.name)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        
        var projections = new Query.ProjectionList()
        projections.distinct()
        projections.property("firstName")


        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
        query.isDistinct()
        query.resultType == String
    }

    def "test createQuery with association projection triggers auto-join"() {
        given:
      
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(JpaCriteriaQueryCreatorSpecPet.name)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPet)
        
        var projections = new Query.ProjectionList()
        projections.property("owner.firstName")

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        noExceptionThrown()
        query != null
    }

    def "test createQuery with order by"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        detachedCriteria.order(Query.Order.asc("firstName"))
        var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
    }

    def "test createQuery with group by"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        var projections = new Query.ProjectionList()
        projections.groupProperty("lastName")
        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
        query.resultType == String
    }

    def "test createQuery orders and groups by the column alias of a SQL projection"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        detachedCriteria.order(Query.Order.desc("total"))
        detachedCriteria.eq("lastName", "Smith")
        var projections = new Query.ProjectionList()
        projections.add(new SqlGroupProjection("NAME"))
        projections.add(new SqlProjection("upper(last_name)", "name", String))
        projections.add(new SqlProjection("count(*)", "total", Long))
        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()
        var selections = query.selection.compoundSelectionItems

        then:
        query.resultType == jakarta.persistence.Tuple
        selections*.alias == ["name", "total"]
        query.groupList.size() == 1
        query.groupList[0].arguments[0].literalValue == "upper(last_name)"
        query.orderList.size() == 1
        query.orderList[0].expression.is(selections[1])
    }

    def "test createQuery groups by the SQL a quoted column alias names"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        detachedCriteria.order(Query.Order.desc("total count"))
        var projections = new Query.ProjectionList()
        projections.add(new SqlGroupProjection('"full name"'))
        SqlProjection.of('upper(last_name) as "full name", count(*) as [total count]', ["full name", "total count"],
                [String, Long]).each { projections.add(it) }
        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()
        var selections = query.selection.compoundSelectionItems

        then:
        selections*.alias == ["full name", "total count"]
        query.groupList.size() == 1
        query.groupList[0].arguments[0].literalValue == "upper(last_name)"
        query.orderList.size() == 1
        query.orderList[0].expression.is(selections[1])
    }

    def "test createQuery matches a group by name #groupBy to a column alias case sensitively only in double quotes"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        var projections = new Query.ProjectionList()
        projections.add(new SqlGroupProjection(groupBy))
        projections.add(new SqlProjection("upper(last_name)", "fullName", String))
        projections.add(new SqlProjection("count(*)", "total", Long))
        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query.groupList.size() == 1
        query.groupList[0].arguments[0].literalValue == groupedBy

        where:
        groupBy      | groupedBy
        'FULLNAME'   | 'upper(last_name)'
        '`FULLNAME`' | 'upper(last_name)'
        '[FULLNAME]' | 'upper(last_name)'
        '"fullName"' | 'upper(last_name)'
        '"FULLNAME"' | '"FULLNAME"'
    }

    def "test createQuery keeps a quoted group by name that is no column alias"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        var projections = new Query.ProjectionList()
        projections.add(new SqlGroupProjection('"last_name"'))
        projections.add(new SqlProjection("upper(last_name)", "name", String))
        projections.add(new SqlProjection("count(*)", "total", Long))
        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query.groupList.size() == 1
        query.groupList[0].arguments[0].literalValue == '"last_name"'
    }

    def "test populateSubquery"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        detachedCriteria.eq("firstName", "Bob")

        var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        // Create a parent query to get a subquery from
        var parentCq = criteriaBuilder.createQuery(JpaCriteriaQueryCreatorSpecPerson)
        var subquery = parentCq.subquery(Long)

        when:
        creator.populateSubquery(subquery)

        then:
        noExceptionThrown()
    }

    def "test populateSubquery with group projection does not cast to criteria query"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        var projections = new Query.ProjectionList()
        projections.groupProperty("lastName")
        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        var parentCq = criteriaBuilder.createQuery(JpaCriteriaQueryCreatorSpecPerson)
        var subquery = parentCq.subquery(String)

        when:
        creator.populateSubquery(subquery)

        then:
        noExceptionThrown()
        subquery.selection != null
        subquery.groupList.size() == 1
    }

    def "test createQuery with id projection returns identifier type"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        var projections = new Query.ProjectionList()
        projections.id()
        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
        query.resultType == Long
    }

    def "test createQuery with aliased count returns long type"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        var projections = new Query.ProjectionList()
        projections.add(new org.grails.orm.hibernate.query.Hibernate7CountProjection("cnt:firstName"))
        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
        query.resultType == Long
    }

    def "test createQuery with avg projection returns double type"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        var projections = new Query.ProjectionList()
        projections.avg("id")
        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
        query.resultType == Double
    }

    def "test createQuery with aliased projection"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        
        var projections = new Query.ProjectionList()
        // Property with alias is supported
        projections.property("cnt:firstName")

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        noExceptionThrown()
        query != null
    }

    def "test createQuery with aliased group property and order by alias"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        
        var projections = new Query.ProjectionList()
        // Group by property with alias
        projections.groupProperty("groupAlias:lastName")
        
        // Order by the alias
        detachedCriteria.order(Query.Order.asc("groupAlias"))

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        noExceptionThrown()
        query != null
    }

    def "test createQuery with aliased countDistinct and order by alias"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        
        var projections = new Query.ProjectionList()
        projections.countDistinct("distinctCnt:firstName")
        
        // Order by the alias
        detachedCriteria.order(Query.Order.asc("distinctCnt"))

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        noExceptionThrown()
        query != null
    }

    def "test getParameterValues is empty before a query is built"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecGraded)
        var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, entity, new DetachedCriteria(JpaCriteriaQueryCreatorSpecGraded), new DefaultConversionService())

        expect:
        creator.getParameterValues().isEmpty()
    }

    def "test createQuery records a parameter for an ordering comparison with a value that is not Comparable"() {
        given:
        var grade = new UserTypeComparisonGrade(2)
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecGraded)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecGraded).gt("grade", grade)
        var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        creator.getParameterValues().values().toList() == [grade]
        query.getParameters() == creator.getParameterValues().keySet()
    }

    def "test createQuery records no parameter for comparisons the JPA value overloads take"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecGraded)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecGraded)
                .gt("level", 2)
                .eq("grade", new UserTypeComparisonGrade(2))
        var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        creator.createQuery()

        then:
        creator.getParameterValues().isEmpty()
    }

    def "test populateSubquery records its parameters in the parent context"() {
        given:
        var grade = new UserTypeComparisonGrade(2)
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecGraded)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecGraded).lt("grade", grade)
        var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())
        var parentCq = criteriaBuilder.createQuery(JpaCriteriaQueryCreatorSpecGraded)
        var parentContext = new JpaQueryContext(parentCq.from(JpaCriteriaQueryCreatorSpecGraded))
        creator.setParentContext(parentContext)

        when:
        creator.populateSubquery(parentCq.subquery(Long))

        then:
        parentContext.getParameterValues().values().toList() == [grade]
        creator.getParameterValues() == parentContext.getParameterValues()
    }
}

@Entity
class JpaCriteriaQueryCreatorSpecPerson implements GormEntity<JpaCriteriaQueryCreatorSpecPerson> {
    Long id
    String firstName
    String lastName
    Set<String> nicknames
    static hasMany = [nicknames: String]
}

@Entity
class JpaCriteriaQueryCreatorSpecPet implements GormEntity<JpaCriteriaQueryCreatorSpecPet> {
    Long id
    String name
    JpaCriteriaQueryCreatorSpecPerson owner
}

@Entity
class JpaCriteriaQueryCreatorSpecGraded implements GormEntity<JpaCriteriaQueryCreatorSpecGraded> {
    Long id
    Integer level
    UserTypeComparisonGrade grade

    static mapping = {
        grade type: UserTypeComparisonGradeType
    }
}
