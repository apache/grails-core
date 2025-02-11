package grails.gorm.specs

import grails.gorm.DetachedCriteria
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.orm.hibernate.AbstractHibernateSession
import org.grails.orm.hibernate.GormSpec
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.query.HibernateQuery

import jakarta.persistence.criteria.JoinType

class DetachedCriteriaJoinSpec  extends HibernateGormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Team,Club]
    }

    def "check if count works as expected"() {
        given:
        def club1 = new Club(name: "Real Madrid").save()
        def club2 = new Club(name: "Barcelona").save()
        def club3 = new Club(name: "Chelsea").save()
        def club4 = new Club(name: "Manchester United").save(flush: true)


        expect:"max and offset should always be ignored when calling count()"
        Club.where {}.max(10).offset(0).count() == 4
        new DetachedCriteria<>(Club).max(10).offset(0).count() == 4
        Club.where {}.max(2).offset(0).count() == 4
        new DetachedCriteria<>(Club).max(2).offset(0).count() == 4
//TODO THESE SHOULD NOT PASS!
//        Club.where {}.max(10).offset(10).count() == 4
//        new DetachedCriteria<>(Club).max(10).offset(10).count() == 4
    }

    def 'check if inner join is applied correctly'(){
        given: 
            def dc = new DetachedCriteria(Team).build{
                join('club', JoinType.INNER)
                createAlias('club','c')
            }
            HibernateQuery query = HibernateGormDatastoreSpec.session.createQuery(Team)
            
            DynamicFinder.applyDetachedCriteria(query,dc)
            def joinType = query.hibernateCriteria.joinTypes['club']
        expect: 
            joinType == JoinType.INNER
    }

    def 'check if left join is applied correctly'(){
        given:
            def dc = new DetachedCriteria(Team).build{
                join('club', JoinType.LEFT)
                createAlias('club','c')
            }
            HibernateQuery query = HibernateGormDatastoreSpec.session.createQuery(Team)

            DynamicFinder.applyDetachedCriteria(query,dc)
            def joinType = query.hibernateCriteria.joinTypes["club"]
        expect:
            joinType == JoinType.LEFT
    }

    def 'check if right join is applied correctly'(){
        given:
            def dc = new DetachedCriteria(Team).build{
                join('club', JoinType.RIGHT)
                createAlias('club','c')
            }
            HibernateQuery query = HibernateGormDatastoreSpec.session.createQuery(Team)

            DynamicFinder.applyDetachedCriteria(query,dc)
            def joinType = query.hibernateCriteria.joinTypes["club"]
        expect:
            joinType == JoinType.RIGHT
    }
}
