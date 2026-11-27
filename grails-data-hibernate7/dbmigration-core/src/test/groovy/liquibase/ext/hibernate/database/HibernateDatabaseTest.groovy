package liquibase.ext.hibernate.database

import groovy.transform.CompileStatic
import liquibase.database.DatabaseFactory
import org.junit.Test

import static org.junit.Assert.*

@CompileStatic
class HibernateDatabaseTest {

    @Test
    void getDefaultDriver() {
        assertEquals('liquibase.ext.hibernate.database.connection.HibernateDriver', DatabaseFactory.getInstance().findDefaultDriver('hibernate:ejb3:pers'))
    }

}
