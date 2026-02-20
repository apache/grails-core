package grails.gorm.specs.hibernatequery

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.apache.grails.data.testing.tck.domains.Person
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.query.JpaCriteriaQueryCreator
import org.grails.orm.hibernate.query.PredicateGenerator
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.hibernate.query.criteria.JpaCriteriaQuery

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
        var predicateGenerator = new PredicateGenerator()
        
        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, predicateGenerator)

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
        var predicateGenerator = new PredicateGenerator()

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, predicateGenerator)

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
        var predicateGenerator = new PredicateGenerator()

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, predicateGenerator)

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
        var predicateGenerator = new PredicateGenerator()

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, predicateGenerator)

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
        var predicateGenerator = new PredicateGenerator()

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, predicateGenerator)

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
        var predicateGenerator = new PredicateGenerator()

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, predicateGenerator)

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
        var predicateGenerator = new PredicateGenerator()

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, predicateGenerator)

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
        var predicateGenerator = new PredicateGenerator()

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, predicateGenerator)

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
        var predicateGenerator = new PredicateGenerator()

        var creator = new JpaCriteriaQueryCreator(projections, criteriaBuilder, entity, detachedCriteria, predicateGenerator)

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
        query.isDistinct()
    }
}
