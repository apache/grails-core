package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 20/10/16.
 */
class SchemaNameSpec extends Specification {

    @Shared @AutoCleanup HibernateDatastore datastore = new HibernateDatastore(['dataSource.url':'jdbc:h2:mem:grailsDB;LOCK_TIMEOUT=10000;INIT=create schema if not exists myschema', (Settings.SETTING_DB_CREATE):'create-drop'],CustomSchema)
    @Shared PlatformTransactionManager transactionManager = datastore.getTransactionManager()

    @Rollback
    @Issue('https://github.com/grails/grails-core/issues/10083')
    void 'test schema name alteration with h2'() {
        when:"An object with a custom schema is saved"
        new CustomSchema(name: "Test").save(flush:true)

        then:"The object was persisted"
        CustomSchema.count() == 1
    }


}
@Entity
class CustomSchema {
    String name
    static mapping = {
        table schema:'myschema'
    }
}


