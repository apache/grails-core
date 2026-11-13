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
 * Verifies that @IdGeneratorType-based custom generators (e.g. @SnowflakeId)
 * are NOT detected as database-native auto-increment or sequences when they
 * are application-assigned (the default when @GeneratedValue is absent).
 */
public class CustomIdGeneratorTest {

    @Test
    public void customIdGeneratorIsNotAutoIncrementWhenGeneratedValueIsAbsent() throws Exception {
        String packages = "com.example.ejb3.customid";
        Database database = new HibernateSpringPackageDatabase();
        database.setDefaultSchemaName("PUBLIC");
        database.setDefaultCatalogName("TESTDB");
        database.setConnection(new JdbcConnection(new HibernateConnection(
                "hibernate:spring:" + packages + "?dialect=" + HSQLDialect.class.getName(),
                new ClassLoaderResourceAccessor())));

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance()
                .createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        Table customIdTable = (Table) snapshot.get(
                new Table().setName("custom_id_entity").setSchema(new Schema()));
        assertNotNull("custom_id_entity table should exist", customIdTable);

        Column idColumn = customIdTable.getColumn("id");
        assertNotNull("id column should exist", idColumn);
        assertFalse("@SnowflakeId (without @GeneratedValue) should NOT be auto-increment",
                idColumn.isAutoIncrement());
    }
}
