package liquibase.ext.hibernate.database;

import org.hibernate.dialect.DatabaseVersion;
import org.hibernate.dialect.Dialect;

/**
 * Generic hibernate dialect used when an actual dialect cannot be determined.
 */
public class HibernateGenericDialect extends Dialect {
    public HibernateGenericDialect() {
        super(DatabaseVersion.make( 7, 2 ));
    }
}
