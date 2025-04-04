package grails.gorm.tests

import org.grails.orm.hibernate.proxy.HibernateProxyHandler
import org.hibernate.proxy.HibernateProxy

/**
 * Created by graemerocher on 16/12/16.
 */
class ToOneProxySpec extends GormDatastoreSpec {

    void "test that a proxy is not initialized on get"() {
        given:
        Team t = new Team(name: "First Team", club: new Club(name: "Manchester United").save())
        t.save(flush:true)
        session.clear()


        when:"An object is retrieved and the session is flushed"
        t = Team.get(t.id)
        session.flush()

        def proxyHandler = new HibernateProxyHandler()
        then:"The association was not initialized"
        proxyHandler.getAssociationProxy(t, "club") != null
        !proxyHandler.isInitialized(t, "club")


    }

    @Override
    List getDomainClasses() {
        [Team, Club]
    }
}
