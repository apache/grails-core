package grails.gorm.specs

import org.apache.grails.data.hibernate6.core.GrailsDataHibernate6TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
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
class HibernateGormDatastoreSpec extends GrailsDataTckSpec<GrailsDataHibernate6TckManager> {

    def setupSpec() {
        manager.grailsConfig = [
                'dataSource.url'               : "jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate'          : 'create-drop',
                'dataSource.formatSql'         : 'true',
                'dataSource.logSql'            : 'true',
                'hibernate.flush.mode'         : 'COMMIT',
                'hibernate.cache.queries'      : 'true',
                'hibernate.hbm2ddl.auto'       : 'create',
                'hibernate.jpa.compliance.cascade': 'true',
        ]
    }
}
