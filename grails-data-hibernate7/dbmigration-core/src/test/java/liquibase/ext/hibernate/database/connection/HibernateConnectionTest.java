package liquibase.ext.hibernate.database.connection;

import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HibernateConnectionTest {

    private final String FILE_PATH = "/path/to/file.ext";

    @Before
    public void setUp() throws Exception {

    }

    @After
    public void tearDown() throws Exception {

    }

    @Test
    public void testHibernateUrlSimple() {
        HibernateConnection conn = new HibernateConnection("hibernate:classic:" + FILE_PATH, new ClassLoaderResourceAccessor());
        Assert.assertEquals("hibernate:classic", conn.getPrefix());
        assertEquals(FILE_PATH, conn.getPath());
        assertEquals(0, conn.getProperties().size());
    }

    @Test
    public void testHibernateUrlWithProperties() {
        HibernateConnection conn = new HibernateConnection("hibernate:classic:" + FILE_PATH + "?foo=bar&name=John+Doe", new ClassLoaderResourceAccessor());
        assertEquals("hibernate:classic", conn.getPrefix());
        assertEquals(FILE_PATH, conn.getPath());
        assertEquals(2, conn.getProperties().size());
        assertEquals("bar", conn.getProperties().getProperty("foo", null));
        assertEquals("John Doe", conn.getProperties().getProperty("name", null));
    }

    @Test
    public void testEjb3UrlSimple() {
        HibernateConnection conn = new HibernateConnection("hibernate:ejb3:" + FILE_PATH, new ClassLoaderResourceAccessor());
        assertEquals("hibernate:ejb3", conn.getPrefix());
        assertEquals(FILE_PATH, conn.getPath());
        assertEquals(0, conn.getProperties().size());
    }

    @Test
    public void testEjb3UrlWithProperties() {
        HibernateConnection conn = new HibernateConnection("hibernate:ejb3:" + FILE_PATH + "?foo=bar&name=John+Doe", new ClassLoaderResourceAccessor());
        assertEquals("hibernate:ejb3", conn.getPrefix());
        assertEquals(FILE_PATH, conn.getPath());
        assertEquals(2, conn.getProperties().size());
        assertEquals("bar", conn.getProperties().getProperty("foo", null));
        assertEquals("John Doe", conn.getProperties().getProperty("name", null));
    }
}
