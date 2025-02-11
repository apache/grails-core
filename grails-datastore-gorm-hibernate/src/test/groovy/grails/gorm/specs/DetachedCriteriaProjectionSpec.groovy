package grails.gorm.specs

import grails.gorm.DetachedCriteria
import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
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
class DetachedCriteriaProjectionSpec extends HibernateGormDatastoreSpec {
    

    @Transactional
    def setup() {
        final entity1 = new Entity1(field1: 'Correct').save(flush:true)
        new Entity1(field1: 'Incorrect', version: 0).save(flush:true)
        new DetachedEntity(entityId: entity1.id, field: 'abc').save(flush:true)
        new DetachedEntity(entityId: entity1.id, field: 'def').save(flush:true)
    }

    @Override
    List getDomainClasses(){
        return [Entity1, Entity2, DetachedEntity]
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
public class Entity1 implements HibernateEntity<Entity1> {
    Long id
    String field1
    static hasMany = [children : Entity2]
}
@Entity
class Entity2 implements HibernateEntity<Entity2> {
    static belongsTo = [parent: Entity1]
    String field
}
@Entity
class DetachedEntity implements HibernateEntity<DetachedEntity> {
    Long entityId
    String field
}