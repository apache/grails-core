package liquibase.ext.hibernate.database;

import liquibase.CatalogAndSchema;
import liquibase.database.Database;
import liquibase.integration.commandline.CommandLineUtils;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Mårten Svantesson
 */
public class JPAPersistenceDatabaseTest {
    @Test
    public void persistenceXML() throws Exception {
        String url = "jpa:persistence:META-INF/persistence.xml";
        Database database = CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(this.getClass().getClassLoader()), url, null, null, null, null, null, false, false, null, null, null, null, null, null, null);

        assertNotNull(database);

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        HibernateEjb3DatabaseTest.assertEjb3HibernateMapped(snapshot);
    }
}
