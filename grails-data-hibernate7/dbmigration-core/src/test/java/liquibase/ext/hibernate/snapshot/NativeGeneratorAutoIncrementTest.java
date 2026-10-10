package liquibase.ext.hibernate.snapshot;

import liquibase.CatalogAndSchema;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.ext.hibernate.database.HibernateSpringPackageDatabase;
import liquibase.ext.hibernate.database.connection.HibernateConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.core.Column;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import org.hibernate.dialect.HSQLDialect;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Verifies that @NativeGenerator columns are correctly detected as
 * auto-increment in the snapshot.
 */
public class NativeGeneratorAutoIncrementTest {

    @Test
    public void nativeGeneratorColumnIsAutoIncrement() throws Exception {
        String packages = "com.example.ejb3.customid";
        Database database = new HibernateSpringPackageDatabase();
        database.setDefaultSchemaName("PUBLIC");
        database.setDefaultCatalogName("TESTDB");
        database.setConnection(new JdbcConnection(new HibernateConnection(
                "hibernate:spring:" + packages + "?dialect=" + HSQLDialect.class.getName(),
                new ClassLoaderResourceAccessor())));

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance()
                .createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        Table nativeGenTable = (Table) snapshot.get(
                new Table().setName("native_gen_entity").setSchema(new Schema()));
        assertNotNull("native_gen_entity table should exist", nativeGenTable);

        Column idColumn = nativeGenTable.getColumn("id");
        assertNotNull("id column should exist", idColumn);
        assertTrue("@NativeGenerator id column should be auto-increment",
                idColumn.isAutoIncrement());
    }
}
