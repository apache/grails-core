package grails.gorm.specs

import grails.gorm.DetachedCriteria
import grails.gorm.transactions.Rollback
import grails.gorm.transactions.Transactional
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification


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

    List getDomainClasses() {
        [Entity1,Entity2,DetachedEntity]
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
        then:
        res.entityId.first() == entity2.id
    }
}