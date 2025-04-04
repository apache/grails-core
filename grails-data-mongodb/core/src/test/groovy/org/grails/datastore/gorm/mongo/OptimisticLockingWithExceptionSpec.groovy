package org.grails.datastore.gorm.mongo

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import org.grails.datastore.mapping.core.OptimisticLockingException
import spock.lang.Issue

import jakarta.persistence.FlushModeType

/**
 * @author Graeme Rocher
 */
class OptimisticLockingWithExceptionSpec extends GormDatastoreSpec{

    @Issue('GPMONGODB-256')
    void "Test that when an optimistic locking exception is thrown the flush mode is set to commit"() {
        when:"An optimistic locking session is thrown"
            Counter c = new Counter(counter: 0).save(flush:true)
            session.clear()

        then:"The version is 1"
            c.version == 0

        when:"The object is concurrently updated"
            c = Counter.get(c.id)
            Thread.start {
                Counter.withNewSession {
                    Counter c1 = Counter.get(c.id)
                    c1.counter++
                    c1.save(flush:true)
                }
            }.join()
            c.counter = 2
            c.save(flush: true)

        then:"An optimistic locking exception was thrown"
            thrown(OptimisticLockingException)
            session.flushMode == FlushModeType.COMMIT


    }

    @Override
    List getDomainClasses() {
        [Counter]
    }
}

@Entity
class Counter {
    Long id
    Long version
    Date lastUpdated
    Date dateCreated
    int counter
}
