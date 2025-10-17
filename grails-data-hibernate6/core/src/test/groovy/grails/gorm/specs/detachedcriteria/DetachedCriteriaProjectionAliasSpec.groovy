package grails.gorm.specs.detachedcriteria

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import grails.gorm.transactions.Rollback
import grails.gorm.transactions.Transactional
import org.hibernate.SessionFactory
import spock.lang.Issue

class DetachedCriteriaProjectionAliasSpec extends HibernateGormDatastoreSpec {

    def entity1
    def entity2

    @Transactional
    def setup() {
        entity1 = new Entity1(field1: 'E1').save(flush:true)
        entity2 = new Entity2(field: 'E2', parent: entity1).save(flush:true)
        entity1.addToChildren(entity2)
        new DetachedEntity(entityId: entity1.id, field: 'DE1').save(flush:true)
        new DetachedEntity( entityId: entity1.id, field: 'DE2').save(flush:true)
    }

    def setupSpec() {
        manager.addAllDomainClasses([Entity1, Entity2, DetachedEntity])
    }

    @Rollback
    @Issue('https://github.com/grails/gorm-hibernate5/issues/598')
    def 'test projection in detached criteria subquery with aliased join and restriction referencing join'() {
        setup:
        final detachedCriteria = new DetachedCriteria(Entity1).build {
            createAlias("children", "e2")
            projections{
                property("id")
            }
            eq("e2.field", "E2")
        }
        when:
        def res = DetachedEntity.withCriteria {
            "in"("entityId", detachedCriteria)
        }
        then:
        res.entityId.first() == entity1.id
    }


    @Rollback
    @Issue('https://github.com/grails/gorm-hibernate5/issues/598')
    def 'test aliased projection in detached criteria subquery'() {
        setup:
        final detachedCriteria = new DetachedCriteria(Entity2).build {
            createAlias("parent", "e1")
            projections{
                property("e1.id")
            }
            eq("field", "E2")
        }
        when:
        def res = DetachedEntity.withCriteria {
            "in"("entityId", detachedCriteria)
        }

        SessionFactory sessionFactory = this.manager.sessionFactory
        def hql = """
select
        de1_0.id,
        de1_0.entity_id,
        de1_0.field,
        de1_0.version
    from
        detached_entity de1_0,
        entity2 e1_0
    where
        de1_0.entity_id in ((select
            p2_0.id
        from
            entity2 e2_0, entity1 p2_0
        where
            p2_0.id=e1_0.parent_id and de1_0.field='E2'))
"""
        def list = sessionFactory.currentSession.createNativeQuery(hql,Object[].class).list()
        println(list)
        then:
        res.entityId.first() == entity2.id
    }
}