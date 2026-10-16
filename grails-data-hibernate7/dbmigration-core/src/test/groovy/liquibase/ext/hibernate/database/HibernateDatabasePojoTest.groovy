package liquibase.ext.hibernate.database

import org.junit.Test

import static org.junit.Assert.assertFalse

/**
 * {@link HibernateDatabase} has a public {@code getProperty(String)} that returns a connection property.
 * A Groovy class would take it for the {@code GroovyObject} hook behind every dynamic property access,
 * so the whole family is compiled without {@code GroovyObject}.
 */
class HibernateDatabasePojoTest {

    @Test
    void everyHibernateDatabaseIsAPlainObject() {
        [HibernateDatabase, HibernateClassicDatabase, HibernateEjb3Database, JpaPersistenceDatabase,
         HibernateSpringBeanDatabase, HibernateSpringPackageDatabase].each { Class<?> type ->
            assertFalse("${type.name} implements GroovyObject", GroovyObject.isAssignableFrom(type))
        }
    }

}
