package liquibase.ext.hibernate.database

import groovy.transform.CompileStatic
import org.hibernate.dialect.DatabaseVersion
import org.hibernate.dialect.Dialect

/**
 * Generic hibernate dialect used when an actual dialect cannot be determined.
 */
@CompileStatic
class HibernateGenericDialect extends Dialect {

    HibernateGenericDialect() {
        super(DatabaseVersion.make(7, 2))
    }

}
