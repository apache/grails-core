package grails.gorm.tck

import grails.gorm.tests.GormDatastoreSpec
import org.grails.datastore.mapping.core.OptimisticLockingException
import spock.lang.IgnoreIf

/**
 * @author Burt Beckwith
 */
class OptimisticLockingSpec extends GormDatastoreSpec {

    void "Test versioning"() {

        given:
        def o = new OptLockVersioned(name: 'locked')

        when:
        o.save flush: true

        then:
        o.version == 0

        when:
        session.clear()
        o = OptLockVersioned.get(o.id)
        o.name = 'Fred'
        o.save flush: true

        then:
        o.version == 1

        when:
        session.clear()
        o = OptLockVersioned.get(o.id)

        then:
        o.name == 'Fred'
        o.version == 1
    }

    // hibernate has a customized version of this
    @IgnoreIf({ System.getProperty('hibernate5.gorm.suite') })
    void "Test optimistic locking"() {

        given:
        def o = new OptLockVersioned(name: 'locked').save(flush: true)
        session.clear()

        when:
        o = OptLockVersioned.get(o.id)

        Thread.start {
            OptLockVersioned.withNewSession { s ->
                def reloaded = OptLockVersioned.get(o.id)
                assert reloaded
                reloaded.name += ' in new session'
                reloaded.save(flush: true)
            }
        }.join()
        sleep 2000 // heisenbug

        o.name += ' in main session'
        def ex
        try {
            o.save(flush: true)
        }
        catch (e) {
            ex = e
            e.printStackTrace()
        }

        session.clear()
        o = OptLockVersioned.get(o.id)

        then:
        ex instanceof OptimisticLockingException
        o.version == 1
        o.name == 'locked in new session'
    }

    void "Test optimistic locking disabled with 'version false'"() {

        given:
        def o = new OptLockNotVersioned(name: 'locked').save(flush: true)
        session.clear()

        when:
        o = OptLockNotVersioned.get(o.id)

        Thread.start {
            OptLockNotVersioned.withNewSession { s ->
                def reloaded = OptLockNotVersioned.get(o.id)
                reloaded.name += ' in new session'
                reloaded.save(flush: true)
            }
        }.join()
        sleep 2000 // heisenbug

        o.name += ' in main session'
        def ex
        try {
            o.save(flush: true)
        }
        catch (e) {
            ex = e
            e.printStackTrace()
        }

        session.clear()
        o = OptLockNotVersioned.get(o.id)

        then:
        ex == null
        o.name == 'locked in main session'
    }
}
