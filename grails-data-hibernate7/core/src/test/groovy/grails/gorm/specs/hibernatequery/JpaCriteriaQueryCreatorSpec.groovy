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
import spock.lang.Shared

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.query.Query
import org.hibernate.query.criteria.JpaCriteriaQuery
import org.grails.orm.hibernate.query.JpaCriteriaQueryCreator
import org.springframework.core.convert.support.DefaultConversionService
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

class JpaCriteriaQueryCreatorSpec extends HibernateGormDatastoreSpec {


    void setupSpec() {
        manager.addAllDomainClasses([JpaCriteriaQueryCreatorSpecPerson, JpaCriteriaQueryCreatorSpecPet])
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

    def "test createQuery with HibernateAlias triggers join"() {
        given:
        var entity = getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        
        // Mock HibernateQuery to provide an alias for a basic collection
        def hibernateQuery = Mock(org.grails.orm.hibernate.query.HibernateQuery) {
            getAliases() >> [new org.grails.orm.hibernate.query.HibernateAlias("nicknames", "n")]
            getEntity() >> entity
        }

        var creator = new JpaCriteriaQueryCreator(new Query.ProjectionList(), criteriaBuilder, entity, detachedCriteria, new DefaultConversionService(), hibernateQuery)

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        noExceptionThrown()
        query != null
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
