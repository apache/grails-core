package grails.gorm.tests

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.neo4j.Neo4jTransaction
import org.grails.datastore.mapping.core.OptimisticLockingException
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.transactions.SessionHolder
import org.neo4j.graphdb.GraphDatabaseService
import org.neo4j.graphdb.Transaction
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * @author Burt Beckwith
 */
class OptimisticLockingSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [OptLockNotVersioned, OptLockVersioned]
    }

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

    void "Test optimistic locking"() {

        given:
        def o = new OptLockVersioned(name: 'locked').save(flush: true)
        session.transaction.commit()
        session.transaction.nativeTransaction.close()
        session.clear()

        def neo4jSession = (org.neo4j.driver.Session) session.getNativeInterface()
        SessionHolder sessionHolder = (SessionHolder) TransactionSynchronizationManager.getResource(session.getDatastore());
//        sessionHolder.setTransaction( new Neo4jTransaction(neo4jSession))

        when:
        o = OptLockVersioned.get(o.id)

        then:
        o != null

        when:
        Thread.start {
            OptLockVersioned.withNewSession { s ->
                OptLockVersioned.withTransaction {
                    def reloaded = OptLockVersioned.get(o.id)
                    assert reloaded
                    reloaded.name += ' in new session'
                    reloaded.save(flush: true)
                }
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

        try {
            Thread.start {
                OptLockNotVersioned.withNewSession { s ->
                    def reloaded = OptLockNotVersioned.get(o.id)
                    reloaded.name += ' in new session'
                    reloaded.save(flush: true)
                }
            }.join(2000)
        } catch (InterruptedException e) {
            // ignore
        }
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

@Entity
class OptLockVersioned implements Serializable {
    Long id
    Long version

    String name
}

@Entity
class OptLockNotVersioned implements Serializable {
    Long id
    Long version

    String name

    static mapping = {
        version false
    }
}
