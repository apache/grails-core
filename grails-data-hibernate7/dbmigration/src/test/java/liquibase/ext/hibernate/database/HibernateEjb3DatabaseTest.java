package liquibase.ext.hibernate.database;

import liquibase.CatalogAndSchema;
import liquibase.database.Database;
import liquibase.integration.commandline.CommandLineUtils;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.core.Schema;
import liquibase.structure.core.Table;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.*;

public class HibernateEjb3DatabaseTest {

    @Test
    public void simpleEjb3Url() throws Exception {
        String url = "hibernate:ejb3:auction";
        Database database = CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(this.getClass().getClassLoader()), url, null, null, null, null, null, false, false, null, null, null, null, null, null, null);

        assertNotNull(database);

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        assertEjb3HibernateMapped(snapshot);
    }

    @Test
    public void nationalizedCharactersEjb3Url() throws Exception {
        String url = "hibernate:ejb3:auction?hibernate.use_nationalized_character_data=true";
        Database database = CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(this.getClass().getClassLoader()), url, null, null, null, null, null, false, false, null, null, null, null, null, null, null);

        assertNotNull(database);

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        assertEjb3HibernateMapped(snapshot);
        Table userTable = (Table) snapshot.get(new Table().setName("user").setSchema(new Schema()));
        assertEquals("nvarchar", userTable.getColumn("userName").getType().getTypeName());
    }

    public static void assertEjb3HibernateMapped(DatabaseSnapshot snapshot) {
        assertThat(snapshot.get(Table.class), containsInAnyOrder(
                hasProperty("name", is("Bid")),
                hasProperty("name", is("Watcher")),
                hasProperty("name", is("User")),
                hasProperty("name", is("AuctionInfo")),
                hasProperty("name", is("AuctionItem")),
                hasProperty("name", is("Item")),
                hasProperty("name", is("AuditedItem")),
                hasProperty("name", is("AuditedItem_AUD")),
                hasProperty("name", is("REVINFO")),
                hasProperty("name", is("WatcherSeqTable")),
                hasProperty("name", is("FirstTable")),
                hasProperty("name", is("second_table"))));


        Table bidTable = (Table) snapshot.get(new Table().setName("bid").setSchema(new Schema()));
        Table auctionInfoTable = (Table) snapshot.get(new Table().setName("auctioninfo").setSchema(new Schema()));
        Table auctionItemTable = (Table) snapshot.get(new Table().setName("auctionitem").setSchema(new Schema()));

        assertThat(bidTable.getColumns(), containsInAnyOrder(
                hasProperty("name", is("id")),
                hasProperty("name", is("item_id")),
                hasProperty("name", is("amount")),
                hasProperty("name", is("datetime")),
                hasProperty("name", is("bidder_id")),
                hasProperty("name", is("DTYPE"))
        ));

        assertTrue(bidTable.getColumn("id").isAutoIncrement());
        assertFalse(auctionInfoTable.getColumn("id").isAutoIncrement());
        assertFalse(bidTable.getColumn("datetime").isNullable());
        assertTrue(auctionItemTable.getColumn("ends").isNullable());

        assertThat(bidTable.getPrimaryKey().getColumnNames(), is("id"));

        assertThat(bidTable.getOutgoingForeignKeys(), containsInAnyOrder(
                allOf(
                        hasProperty("primaryKeyColumns", hasToString("[HIBERNATE.AuctionItem.id]")),
                        hasProperty("foreignKeyColumns", hasToString("[HIBERNATE.Bid.item_id]")),
                        hasProperty("primaryKeyTable", hasProperty("name", is("AuctionItem")))
                ),
                allOf(
                        hasProperty("primaryKeyColumns", hasToString("[HIBERNATE.User.id]")),
                        hasProperty("foreignKeyColumns", hasToString("[HIBERNATE.Bid.bidder_id]")),
                        hasProperty("primaryKeyTable", hasProperty("name", is("User")))
                )
        ));

        Table secondTable = (Table) snapshot.get(new Table().setName("second_table").setSchema(new Schema()));
        assertThat(secondTable.getColumns(), containsInAnyOrder(
                hasProperty("name", is("first_table_id")),
                hasProperty("name", is("secondName"))
        ));
        assertThat(secondTable.getPrimaryKey().getColumnNames(), is("first_table_id"));
    }
}
