package grails.gorm.specs

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.Session
import org.springframework.transaction.support.DefaultTransactionDefinition
import spock.lang.Shared
import spock.lang.Specification

/**
 * The original GormDataStoreSpec destroyed the setup
 * between tests instead of at the end of all tests
 * It also wqs default configured for H2 which
 * made it break with some Java types.
 * Finally, it loaded all the test Entities,
 * now it can be setup individually.
 */
class HibernateGormDatastoreSpec extends Specification{

    static final CURRENT_TEST_NAME = "current.gorm.test"
    static final SETUP_CLASS_NAME = 'org.grails.datastore.gorm.Setup'
    static final TEST_CLASSES = []

    @Shared Class setupClass

    static Session session

    def setupSpec() {
        setupClass = loadSetupClass()
        cleanRegistry()
        System.setProperty(CURRENT_TEST_NAME, this.getClass().simpleName - 'Spec')
        configure()
    }

    def setup() {
        if (setupClass.transactionStatus == null) {
            setupClass.transactionStatus = setupClass.transactionManager.getTransaction(new DefaultTransactionDefinition())
        }
        session = setupClass.hibernateDatastore.connect()
    }


    Session configure() {
        ConfigObject grailsConfig = new ConfigObject()
        Map config = [
                'dataSource.url':"jdbc:tc:postgresql:///dev_db",
                'dataSource.dbCreate': 'create-drop',
                'dataSource.formatSql': 'true',
                'dataSource.logSql': 'true',
                'hibernate.flush.mode': 'COMMIT',
                'hibernate.cache.queries': 'true',
                'hibernate.hbm2ddl.auto': 'create',
                'hibernate.type.descriptor.sql': 'true'
        ]
        grailsConfig.putAll(config)
        setupClass.setup(((TEST_CLASSES + getDomainClasses()) as Set) as List, grailsConfig, true)
    }

    List getDomainClasses() {
        []
    }


    def cleanup() {
        if (setupClass.transactionStatus != null) {
            def tx = setupClass.transactionStatus
            setupClass.transactionStatus = null
            setupClass.transactionManager.rollback(tx)
        }
    }


    def cleanupSpec() {
        if (session) {
            session.disconnect()
            DatastoreUtils.unbindSession session
        }
        try {
            setupClass.destroy()
        } catch(e) {
            println "ERROR: Exception during test cleanup: ${e.message}"
        }

        cleanRegistry()
    }

    private cleanRegistry() {
        for (clazz in (TEST_CLASSES + getDomainClasses() )) {
            GroovySystem.metaClassRegistry.removeMetaClass(clazz)
        }
    }

    static Class loadSetupClass() {
        try {
            getClassLoader().loadClass(SETUP_CLASS_NAME)
        } catch (Throwable e) {
            throw new RuntimeException("Datastore setup class ($SETUP_CLASS_NAME) was not found",e)
        }
    }

}
