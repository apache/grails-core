package liquibase.ext.hibernate.database;

import liquibase.database.DatabaseFactory;
import org.junit.Test;

import static org.junit.Assert.*;

public class HibernateDatabaseTest {

    @Test
    public void getDefaultDriver() {
        assertEquals("liquibase.ext.hibernate.database.connection.HibernateDriver", DatabaseFactory.getInstance().findDefaultDriver("hibernate:ejb3:pers"));
    }
}