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

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.apache.grails.data.testing.tck.domains.Person
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.query.JpaCriteriaQueryCreator
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.hibernate.query.criteria.JpaCriteriaQuery
import org.springframework.core.convert.support.DefaultConversionService

class JpaCriteriaQueryCreatorSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([Person])
    }

    def "test createQuery with property projection"() {
        given:
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName)
        var detachedCriteria = new DetachedCriteria(Person)
        var projections = new Query.ProjectionList()
        projections.property("firstName")

        
        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
        query.getSelection() != null
    }

    def "test createQuery with multiple projections"() {
        given:
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName)
        var detachedCriteria = new DetachedCriteria(Person)
        var projections = new Query.ProjectionList()
        projections.property("firstName")
        projections.property("lastName")


        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
        query.getSelection() != null
    }

    def "test createQuery with count projection"() {
        given:
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName)
        var detachedCriteria = new DetachedCriteria(Person)
        var projections = new Query.ProjectionList()
        projections.count()


        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
    }

    def "test createQuery with countDistinct projection"() {
        given:
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName)
        var detachedCriteria = new DetachedCriteria(Person)
        var projections = new Query.ProjectionList()
        projections.countDistinct("firstName")


        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
    }

    def "test createQuery with id projection"() {
        given:
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName)
        var detachedCriteria = new DetachedCriteria(Person)
        var projections = new Query.ProjectionList()
        projections.id()


        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
    }

    def "test createQuery with aggregate projections"() {
        given:
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName)
        var detachedCriteria = new DetachedCriteria(Person)
        var projections = new Query.ProjectionList()
        projections.max("age")
        projections.min("age")
        projections.avg("age")
        projections.sum("age")


        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
    }

    def "test createQuery with groupProperty and order"() {
        given:
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName)
        var detachedCriteria = new DetachedCriteria(Person)
        detachedCriteria.order(Query.Order.asc("lastName"))
        detachedCriteria.order(new Query.Order("firstName", Query.Order.Direction.DESC).ignoreCase())
        
        var projections = new Query.ProjectionList()
        projections.groupProperty("lastName")
        projections.count()


        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
    }

    def "test createQuery with criteria"() {
        given:
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName)
        var detachedCriteria = new DetachedCriteria(Person)
        detachedCriteria.eq("firstName", "Bob")
        
        var projections = new Query.ProjectionList()


        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
    }

    def "test createQuery with distinct"() {
        given:
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName)
        var detachedCriteria = new DetachedCriteria(Person)
        
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
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(org.apache.grails.data.testing.tck.domains.Pet.typeName)
        var detachedCriteria = new DetachedCriteria(org.apache.grails.data.testing.tck.domains.Pet)
        
        var projections = new Query.ProjectionList()
        projections.property("owner.firstName")

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        noExceptionThrown()
        query != null
    }
}
