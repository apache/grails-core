package grails.gorm.tests.hasmany

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.mapping.collection.PersistentCollection
import org.grails.datastore.mapping.proxy.ProxyHandler
import org.grails.orm.hibernate.HibernateDatastore
import spock.lang.AutoCleanup
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

class ListCollectionSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(getClass().getPackage())

    @Rollback
    void "test legs are not loaded eagerly"() {
        given:
        new Animal(name: "Chloe")
            .addToLegs(new Leg())
            .addToLegs(new Leg())
            .addToLegs(new Leg())
            .addToLegs(new Leg())
            .save(flush: true, failOnError: true)
        datastore.currentSession.flush()
        datastore.currentSession.clear()
        ProxyHandler ph = datastore.mappingContext.proxyHandler

        when:
        Animal animal = Animal.load(1)
        animal = ph.unwrap(animal)

        then:
        ph.isProxy(animal.legs) && !ph.isInitialized(animal.legs)
    }
}

@Entity
class Animal {
    String name

    List legs
    static hasMany = [legs: Leg]
}

@Entity
class Leg {

}