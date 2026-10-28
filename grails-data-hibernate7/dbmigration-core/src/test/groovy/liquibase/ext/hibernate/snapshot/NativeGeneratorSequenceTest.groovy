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
 * Verifies that @NativeGenerator sequences are captured in the snapshot
 * when the dialect uses SEQUENCE as the native strategy (e.g. PostgreSQL).
 */
public class NativeGeneratorSequenceTest {

    @Test
    public void nativeGeneratorSequenceIsCapturedWithPostgresDialect() throws Exception {
        String packages = "com.example.ejb3.customid";
        Database database = new HibernateSpringPackageDatabase();
        database.setDefaultSchemaName("PUBLIC");
        database.setDefaultCatalogName("TESTDB");
        database.setConnection(new JdbcConnection(new HibernateConnection(
                "hibernate:spring:" + packages + "?dialect=" + PostgreSQLDialect.class.getName(),
                new ClassLoaderResourceAccessor())));

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance()
                .createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        Sequence nativeSeq = null;
        for (Sequence seq : snapshot.get(Sequence.class)) {
            if (seq.getName().equalsIgnoreCase("native_gen_seq")) {
                nativeSeq = seq;
            }
        }

        assertNotNull("native_gen_seq should be in snapshot when dialect uses SEQUENCE strategy", nativeSeq);
        assertEquals("initialValue should be 3", BigInteger.valueOf(3), nativeSeq.getStartValue());
        assertEquals("allocationSize should be 22", BigInteger.valueOf(22), nativeSeq.getIncrementBy());
    }
}
