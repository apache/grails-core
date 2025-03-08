package grails.gorm.tests

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import grails.gorm.transactions.Transactional
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 24/10/16.
 */
class DetachedCriteriaProjectionSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(Entity1, Entity2, DetachedEntity)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    @Transactional
    def setup() {
        DetachedEntity.findAll().each { it.delete() }
        Entity1.findAll().each { it.delete(flush: true) }
        final entity1 = new Entity1(id: 1, field1: 'Correct').save()
        new Entity1(id: 2, field1: 'Incorrect').save()
        new DetachedEntity(id: 1, entityId: entity1.id, field: 'abc').save()
        new DetachedEntity(id: 2, entityId: entity1.id, field: 'def').save()
    }

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/792')
    def 'closure projection fails'() {
        setup:
        final detachedCriteria = new DetachedCriteria(DetachedEntity).build {
            projections {
                distinct 'entityId'
            }
            eq 'field', 'abc'
        }
        when:
        // will fail
        def results = Entity1.withCriteria {
            inList 'id', detachedCriteria
        }
        then:
        results.size() == 1

    }

    @Rollback
    def 'closure projection manually'() {
        setup:
        final detachedCriteria = new DetachedCriteria(DetachedEntity).build {
            eq 'field', 'abc'
        }
        detachedCriteria.projections << new Query.DistinctPropertyProjection('entityId')
        expect:
        assert Entity1.withCriteria {
            inList 'id', detachedCriteria
        }.collect { it.field1 }.contains('Correct')
    }

    @Rollback
    def 'or fails in detached criteria'() {
        setup:
        final detachedCriteria = new DetachedCriteria(DetachedEntity).build {
            or {
                eq 'field', 'abc'
                eq 'field', 'def'
            }
        }
        detachedCriteria.projections << new Query.DistinctPropertyProjection('entityId')
        when:
        def results = Entity1.withCriteria {
            inList 'id', detachedCriteria
        }
        then:
        results.size() == 1
    }
}

@Entity
public class Entity1 {
    Long id
    String field1
    static hasMany = [children : Entity2]
}
@Entity
class Entity2 {
    static belongsTo = [parent: Entity1]
    String field
}
@Entity
class DetachedEntity {
    Long entityId
    String field
}