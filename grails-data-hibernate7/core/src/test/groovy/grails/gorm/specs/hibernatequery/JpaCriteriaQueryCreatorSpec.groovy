package grails.gorm.specs.hibernatequery

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import grails.orm.HibernateCriteriaBuilder
import org.hibernate.query.criteria.JpaCriteriaQuery
import org.grails.orm.hibernate.query.JpaCriteriaQueryCreator
import org.springframework.core.convert.support.DefaultConversionService
import spock.lang.Shared
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

class JpaCriteriaQueryCreatorSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.addAllDomainClasses([JpaCriteriaQueryCreatorSpecPerson, JpaCriteriaQueryCreatorSpecPet])
    }

    def "test createQuery"() {
        given:
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
        var entity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(JpaCriteriaQueryCreatorSpecPerson.name)
        var detachedCriteria = new DetachedCriteria(JpaCriteriaQueryCreatorSpecPerson)
        var creator = new JpaCriteriaQueryCreator(criteriaBuilder, entity, detachedCriteria, new DefaultConversionService())

        when:
        JpaCriteriaQuery<?> query = creator.createQuery()

        then:
        query != null
    }

    def "test createQuery with projections"() {
        given:
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
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
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
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
        HibernateCriteriaBuilder criteriaBuilder = sessionFactory.getCriteriaBuilder()
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
}

@Entity
class JpaCriteriaQueryCreatorSpecPerson implements GormEntity<JpaCriteriaQueryCreatorSpecPerson> {
    Long id
    String firstName
    String lastName
}

@Entity
class JpaCriteriaQueryCreatorSpecPet implements GormEntity<JpaCriteriaQueryCreatorSpecPet> {
    Long id
    String name
    JpaCriteriaQueryCreatorSpecPerson owner
}
