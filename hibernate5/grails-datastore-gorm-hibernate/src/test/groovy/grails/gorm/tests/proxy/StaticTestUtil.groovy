package grails.gorm.tests.proxy

import groovy.transform.CompileStatic

import org.grails.datastore.gorm.GormEntity
import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import org.hibernate.Hibernate

import grails.gorm.tests.Club
import grails.gorm.tests.Team

@CompileStatic
class StaticTestUtil {
    public static HibernateProxyHandler proxyHandler = new HibernateProxyHandler()

    // should return true and not initialize the proxy
    // getId works inside a compile static
    static boolean team_id_asserts(Team team){
        assert team.getId()
        assert !Hibernate.isInitialized(team)
        assert proxyHandler.isProxy(team)

        assert team.id
        assert !Hibernate.isInitialized(team)
        assert proxyHandler.isProxy(team)
        //a truthy check on the object will try to init it because it hits the getMetaClass
        // assert team
        // assert !Hibernate.isInitialized(team)

        return true
    }

    static boolean club_id_asserts(Team team){
        assert team.club.getId()
        assert notInitialized(team.club)

        assert team.club.id
        assert notInitialized(team.club)

        assert team.clubId
        assert notInitialized(team.club)

        return true
    }

    static boolean notInitialized(Object o){
        //sanity check the 3
        assert !Hibernate.isInitialized(o)
        assert !proxyHandler.isInitialized(o)
        assert proxyHandler.isProxy(o)
        return true
    }
}

