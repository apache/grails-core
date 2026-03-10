package liquibase.ext.hibernate.database;

import liquibase.CatalogAndSchema;
import liquibase.database.Database;
import liquibase.database.DatabaseConnection;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.ext.hibernate.database.connection.HibernateConnection;
import liquibase.integration.commandline.CommandLineUtils;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import org.hibernate.dialect.H2Dialect;
import org.junit.After;
import org.junit.Test;

import com.example.ejb3.auction.Bid;
import com.example.ejb3.auction.BuyNow;
import com.example.pojo.auction.AuctionItem;
import com.example.pojo.auction.Watcher;

import static org.junit.Assert.*;

public class HibernateSpringDatabaseTest {

    private DatabaseConnection conn;
    private HibernateDatabase db;

    @After
    public void tearDown() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void testSpringUrlSimple() throws DatabaseException {
        conn = new JdbcConnection(new HibernateConnection("hibernate:spring:spring.ctx.xml?bean=sessionFactory", new ClassLoaderResourceAccessor()));
        db = new HibernateSpringBeanDatabase();
        db.setConnection(conn);
        assertNotNull(db.getMetadata().getEntityBinding(AuctionItem.class.getName()));
        assertNotNull(db.getMetadata().getEntityBinding(Watcher.class.getName()));
    }


    @Test
    public void testSpringPackageScanningMustHaveItemClassMapping() throws DatabaseException {
        conn = new JdbcConnection(new HibernateConnection("hibernate:spring:com.example.ejb3.auction?dialect=" + H2Dialect.class.getName(), new ClassLoaderResourceAccessor()));
        db = new HibernateSpringPackageDatabase();
        db.setConnection(conn);
        assertNotNull(db.getMetadata().getEntityBinding(Bid.class.getName()));
        assertNotNull(db.getMetadata().getEntityBinding(BuyNow.class.getName()));
    }

    @Test
    public void simpleSpringUrl() throws Exception {
        String url = "hibernate:spring:spring.ctx.xml?bean=sessionFactory";
        Database database = CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(this.getClass().getClassLoader()), url, null, null, null, null, null, false, false, null, null, null, null, null, null, null);

        assertNotNull(database);

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        HibernateClassicDatabaseTest.assertPojoHibernateMapped(snapshot);
    }

    @Test
    public void nationalizedCharactersSpringBeanUrl() throws Exception {
        String url = "hibernate:spring:spring.ctx.xml?hibernate.use_nationalized_character_data=true&bean=sessionFactory";
        Database database = CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(this.getClass().getClassLoader()), url, null, null, null, null, null, false, false, null, null, null, null, null, null, null);

        assertNotNull(database);

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        HibernateClassicDatabaseTest.assertPojoHibernateMapped(snapshot);
        Table watcherTable = (Table) snapshot.get(new Table().setName("watcher").setSchema(new Schema()));
        assertEquals("nvarchar", watcherTable.getColumn("name").getType().getTypeName());
    }

    @Test
    public void simpleSpringScanningUrl() throws Exception {
        String url = "hibernate:spring:com.example.ejb3.auction?dialect=" + H2Dialect.class.getName();
        Database database = CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(this.getClass().getClassLoader()), url, null, null, null, null, null, false, false, null, null, null, null, null, null, null);

        assertNotNull(database);

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        HibernateEjb3DatabaseTest.assertEjb3HibernateMapped(snapshot);
    }

    @Test
    public void nationalizedCharactersSpringScanningUrl() throws Exception {
        String url = "hibernate:spring:com.example.ejb3.auction?hibernate.use_nationalized_character_data=true&dialect=" + H2Dialect.class.getName();
        Database database = CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(this.getClass().getClassLoader()), url, null, null, null, null, null, false, false, null, null, null, null, null, null, null);

        assertNotNull(database);

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        HibernateEjb3DatabaseTest.assertEjb3HibernateMapped(snapshot);
        Table userTable = (Table) snapshot.get(new Table().setName("user").setSchema(new Schema()));
        assertEquals("varchar", userTable.getColumn("userName").getType().getTypeName());
    }

}
