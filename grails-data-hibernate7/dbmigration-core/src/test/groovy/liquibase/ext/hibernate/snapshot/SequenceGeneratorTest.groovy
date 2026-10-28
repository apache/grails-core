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
import liquibase.structure.core.Sequence;
import org.hibernate.dialect.PostgreSQLDialect;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.*;

/**
 * Verifies that @SequenceGenerator-based sequences are captured in the snapshot.
 * This matches the UserC pattern.
 */
public class SequenceGeneratorTest {

    @Test
    public void sequenceGeneratorIsCaptured() throws Exception {
        String packages = "com.example.ejb3.auction";
        Database database = new HibernateSpringPackageDatabase();
        database.setDefaultSchemaName("PUBLIC");
        database.setDefaultCatalogName("TESTDB");
        database.setConnection(new JdbcConnection(new HibernateConnection(
                "hibernate:spring:" + packages + "?dialect=" + PostgreSQLDialect.class.getName(),
                new ClassLoaderResourceAccessor())));

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance()
                .createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        Sequence itemSeq = null;
        for (Sequence seq : snapshot.get(Sequence.class)) {
            if (seq.getName().equalsIgnoreCase("ITEM_SEQ")) {
                itemSeq = seq;
            }
        }

        assertNotNull("ITEM_SEQ should be in snapshot", itemSeq);
        assertEquals("initialValue should be 1000", BigInteger.valueOf(1000), itemSeq.getStartValue());
        assertEquals("allocationSize should be 100", BigInteger.valueOf(100), itemSeq.getIncrementBy());
    }
}
