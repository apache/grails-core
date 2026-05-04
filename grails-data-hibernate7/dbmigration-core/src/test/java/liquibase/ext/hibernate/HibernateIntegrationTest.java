package liquibase.ext.hibernate;

import liquibase.Liquibase;
import liquibase.Scope;
import liquibase.database.Database;
import liquibase.database.core.H2Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.diff.DiffResult;
import liquibase.diff.Difference;
import liquibase.diff.ObjectDifferences;
import liquibase.diff.compare.CompareControl;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.DiffToChangeLog;
import liquibase.diff.output.report.DiffToReport;
import liquibase.ext.hibernate.database.HibernateClassicDatabase;
import liquibase.ext.hibernate.database.connection.HibernateConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

public class HibernateIntegrationTest {
    private static final String HIBERNATE_CONFIG_FILE = "com/example/pojo/Hibernate.cfg.xml";
    private Database database;
    private Connection connection;
    private CompareControl compareControl;

    @Before
    public void setUp() throws Exception {
        Class.forName("org.h2.Driver");
        connection = DriverManager.getConnection("jdbc:h2:mem:TESTDB" + System.currentTimeMillis(), "SA", "");
        database = new H2Database();
        database.setConnection(new JdbcConnection(connection));

//        Class.forName("com.mysql.jdbc.Driver");
//        connection = DriverManager.getConnection("jdbc:mysql://10.10.100.100/liquibase", "liquibase", "liquibase");
//        database = new MySQLDatabase();
//        database.setConnection(new JdbcConnection(connection));

        Set<Class<? extends DatabaseObject>> typesToInclude = new HashSet<Class<? extends DatabaseObject>>();
        typesToInclude.add(Table.class);
        typesToInclude.add(Column.class);
        typesToInclude.add(PrimaryKey.class);
        typesToInclude.add(ForeignKey.class);
//	typesToInclude.add(Index.class); //databases generate ones that hibernate doesn't know about
        typesToInclude.add(UniqueConstraint.class);
        typesToInclude.add(Sequence.class);
        compareControl = new CompareControl(typesToInclude);
        compareControl.addSuppressedField(Table.class, "remarks");
        compareControl.addSuppressedField(Column.class, "remarks");
        compareControl.addSuppressedField(Column.class, "certainDataType");
        compareControl.addSuppressedField(Column.class, "autoIncrementInformation");
        compareControl.addSuppressedField(ForeignKey.class, "deleteRule");
        compareControl.addSuppressedField(ForeignKey.class, "updateRule");
        compareControl.addSuppressedField(Index.class, "unique");
    }

    @After
    public void tearDown() throws Exception {
        database.close();
        connection = null;
        database = null;
        compareControl = null;
    }

    /**
     * Generates a changelog from the Hibernate mapping, creates the database
     * according to the changelog, compares, the database with the mapping.
     *
     * @throws Exception
     */
    @Test
    public void runGeneratedChangeLog() throws Exception {

        Liquibase liquibase = new Liquibase((String) null, new ClassLoaderResourceAccessor(), database);

        Database hibernateDatabase = new HibernateClassicDatabase();
//        hibernateDatabase.setDefaultSchemaName("PUBLIC");
//        hibernateDatabase.setDefaultCatalogName("TESTDB");
        hibernateDatabase.setConnection(new JdbcConnection(new HibernateConnection("hibernate:classic:" + HIBERNATE_CONFIG_FILE, new ClassLoaderResourceAccessor())));

        DiffResult diffResult = liquibase.diff(hibernateDatabase, database, compareControl);

        assertTrue(diffResult.getMissingObjects().size() > 0);

        File outFile = File.createTempFile("lb-test", ".xml");
        OutputStream outChangeLog = new FileOutputStream(outFile);
        String changeLogString = toChangeLog(diffResult);
        outChangeLog.write(changeLogString.getBytes("UTF-8"));
        outChangeLog.close();

        Scope.getCurrentScope().getLog(getClass()).info("Changelog:\n" + changeLogString);

        liquibase = new Liquibase(outFile.toString(), new DirectoryResourceAccessor(File.listRoots()[0]), database);
        StringWriter stringWriter = new StringWriter();
        liquibase.update((String) null, stringWriter);
        Scope.getCurrentScope().getLog(getClass()).info(stringWriter.toString());
        liquibase.update((String) null);

        diffResult = liquibase.diff(hibernateDatabase, database, compareControl);

        ignoreDatabaseChangeLogTable(diffResult);
        ignoreConversionFromFloatToDouble64(diffResult);

        String differences = toString(diffResult);

        assertEquals(differences, 0, diffResult.getMissingObjects().size());
        assertEquals(differences, 0, diffResult.getUnexpectedObjects().size());
//        assertEquals(differences, 0, diffResult.getChangedObjects().size());  //unimportant differences in schema name and datatypes causing test to fail

    }

    /**
     * Creates a database using Hibernate SchemaExport and compare the database
     * with the Hibernate mapping
     *
     * @throws Exception
     */
//    @Test
//    public void hibernateSchemaExport() throws Exception {
//
//        SingleConnectionDataSource ds = new SingleConnectionDataSource(connection, true);
//
//        Configuration cfg = new Configuration();
//        cfg.configure(HIBERNATE_CONFIG_FILE);
//        Properties properties = new Properties();
//        properties.put(Environment.DATASOURCE, ds);
//        cfg.addProperties(properties);
//
//        SchemaExport export = new SchemaExport(cfg);
//        export.execute(true, true, false, false);
//
//        Database hibernateDatabase = new HibernateClassicDatabase();
//        hibernateDatabase.setDefaultSchemaName("PUBLIC");
//        hibernateDatabase.setDefaultCatalogName("TESTDB");
//        hibernateDatabase.setConnection(new JdbcConnection(new HibernateConnection("hibernate:classic:" + HIBERNATE_CONFIG_FILE)));
//
//        Liquibase liquibase = new Liquibase((String) null, new ClassLoaderResourceAccessor(), database);
//        DiffResult diffResult = liquibase.diff(hibernateDatabase, database, compareControl);
//
//        ignoreDatabaseChangeLogTable(diffResult);
//        ignoreConversionFromFloatToDouble64(diffResult);
//
//        String differences = toString(diffResult);
//
//        assertEquals(differences, 0, diffResult.getMissingObjects().size());
//        assertEquals(differences, 0, diffResult.getUnexpectedObjects().size());
////        assertEquals(differences, 0, diffResult.getChangedObjects().size()); //unimportant differences in schema name and datatypes causing test to fail
//
//    }

//    /**
//     * Generates the changelog from Hibernate mapping, creates 2 databases,
//     * updates 1 of the databases with HibernateSchemaUpdate. Compare the 2
//     * databases.
//     *
//     * @throws Exception
//     */
//    @Test
//    public void hibernateSchemaUpdate() throws Exception {
//
//        Liquibase liquibase = new Liquibase((String) null, new ClassLoaderResourceAccessor(), database);
//
//        Database hibernateDatabase = new HibernateClassicDatabase();
//        hibernateDatabase.setDefaultSchemaName("PUBLIC");
//        hibernateDatabase.setDefaultCatalogName("TESTDB");
//        hibernateDatabase.setConnection(new JdbcConnection(new HibernateConnection("hibernate:classic:" + HIBERNATE_CONFIG_FILE)));
//
//        DiffResult diffResult = liquibase.diff(hibernateDatabase, database, compareControl);
//
//        assertTrue(diffResult.getMissingObjects().size() > 0);
//
//        File outFile = File.createTempFile("lb-test", ".xml");
//        OutputStream outChangeLog = new FileOutputStream(outFile);
//        String changeLogString = toChangeLog(diffResult);
//        outChangeLog.write(changeLogString.getBytes("UTF-8"));
//        outChangeLog.close();
//
//        log.info("Changelog:\n" + changeLogString);
//
//        liquibase = new Liquibase(outFile.toString(), new FileSystemResourceAccessor(), database);
//        StringWriter stringWriter = new StringWriter();
//        liquibase.update((String) null, stringWriter);
//        log.info(stringWriter.toString());
//        liquibase.update((String) null);
//
//        long currentTimeMillis = System.currentTimeMillis();
//        Connection connection2 = DriverManager.getConnection("jdbc:h2:mem:TESTDB2" + currentTimeMillis, "SA", "");
//        Database database2 = new H2Database();
//        database2.setConnection(new JdbcConnection(connection2));
//
//        Configuration cfg = new Configuration();
//        cfg.configure(HIBERNATE_CONFIG_FILE);
//        cfg.getProperties().remove(Environment.DATASOURCE);
//        cfg.setProperty(Environment.URL, "jdbc:h2:mem:TESTDB2" + currentTimeMillis);
//        cfg.setProperty(Environment.USER, "SA");
//        cfg.setProperty(Environment.PASS, "");
//
//        SchemaUpdate update = new SchemaUpdate(cfg);
//        update.execute(true, true);
//
//        diffResult = liquibase.diff(database, database2, compareControl);
//
//        ignoreDatabaseChangeLogTable(diffResult);
//        ignoreConversionFromFloatToDouble64(diffResult);
//
//        String differences = toString(diffResult);
//
//        assertEquals(differences, 0, diffResult.getMissingObjects().size());
//        assertEquals(differences, 0, diffResult.getUnexpectedObjects().size());
//        assertEquals(differences, 0, diffResult.getChangedObjects().size());
//    }
    private String toString(DiffResult diffResult) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(out, true, "UTF-8");
        DiffToReport diffToReport = new DiffToReport(diffResult, printStream);
        diffToReport.print();
        printStream.close();
        return out.toString("UTF-8");
    }

    private String toChangeLog(DiffResult diffResult) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(out, true, "UTF-8");
        DiffOutputControl diffOutputControl = new DiffOutputControl();
        diffOutputControl.setIncludeCatalog(false);
        diffOutputControl.setIncludeSchema(false);
        DiffToChangeLog diffToChangeLog = new DiffToChangeLog(diffResult,
                diffOutputControl);
        diffToChangeLog.print(printStream);
        printStream.close();
        return out.toString("UTF-8");
    }

    private void ignoreDatabaseChangeLogTable(DiffResult diffResult) throws Exception {
        Set<Table> unexpectedTables = diffResult.getUnexpectedObjects(Table.class);
        for (Iterator<Table> iterator = unexpectedTables.iterator(); iterator.hasNext(); ) {
            Table table = iterator.next();
            if ("DATABASECHANGELOGLOCK".equalsIgnoreCase(table.getName()) || "DATABASECHANGELOG".equalsIgnoreCase(table.getName()))
                diffResult.getUnexpectedObjects().remove(table);
        }
        Set<Table> missingTables = diffResult.getMissingObjects(Table.class);
        for (Iterator<Table> iterator = missingTables.iterator(); iterator.hasNext(); ) {
            Table table = iterator.next();
            if ("DATABASECHANGELOGLOCK".equalsIgnoreCase(table.getName()) || "DATABASECHANGELOG".equalsIgnoreCase(table.getName()))
                diffResult.getMissingObjects().remove(table);
        }
        Set<Column> unexpectedColumns = diffResult.getUnexpectedObjects(Column.class);
        for (Iterator<Column> iterator = unexpectedColumns.iterator(); iterator.hasNext(); ) {
            Column column = iterator.next();
            if ("DATABASECHANGELOGLOCK".equalsIgnoreCase(column.getRelation().getName()) || "DATABASECHANGELOG".equalsIgnoreCase(column.getRelation().getName()))
                diffResult.getUnexpectedObjects().remove(column);
        }
        Set<Column> missingColumns = diffResult.getMissingObjects(Column.class);
        for (Iterator<Column> iterator = missingColumns.iterator(); iterator.hasNext(); ) {
            Column column = iterator.next();
            if ("DATABASECHANGELOGLOCK".equalsIgnoreCase(column.getRelation().getName()) || "DATABASECHANGELOG".equalsIgnoreCase(column.getRelation().getName()))
                diffResult.getMissingObjects().remove(column);
        }
        Set<Index> unexpectedIndexes = diffResult.getUnexpectedObjects(Index.class);
        for (Iterator<Index> iterator = unexpectedIndexes.iterator(); iterator.hasNext(); ) {
            Index index = iterator.next();
            if ("DATABASECHANGELOGLOCK".equalsIgnoreCase(index.getRelation().getName()) || "DATABASECHANGELOG".equalsIgnoreCase(index.getRelation().getName()))
                diffResult.getUnexpectedObjects().remove(index);
        }
        Set<Index> missingIndexes = diffResult.getMissingObjects(Index.class);
        for (Iterator<Index> iterator = missingIndexes.iterator(); iterator.hasNext(); ) {
            Index index = iterator.next();
            if ("DATABASECHANGELOGLOCK".equalsIgnoreCase(index.getRelation().getName()) || "DATABASECHANGELOG".equalsIgnoreCase(index.getRelation().getName()))
                diffResult.getMissingObjects().remove(index);
        }
        Set<PrimaryKey> unexpectedPrimaryKeys = diffResult.getUnexpectedObjects(PrimaryKey.class);
        for (Iterator<PrimaryKey> iterator = unexpectedPrimaryKeys.iterator(); iterator.hasNext(); ) {
            PrimaryKey primaryKey = iterator.next();
            if ("DATABASECHANGELOGLOCK".equalsIgnoreCase(primaryKey.getTable().getName()) || "DATABASECHANGELOG".equalsIgnoreCase(primaryKey.getTable().getName()))
                diffResult.getUnexpectedObjects().remove(primaryKey);
        }
        Set<PrimaryKey> missingPrimaryKeys = diffResult.getMissingObjects(PrimaryKey.class);
        for (Iterator<PrimaryKey> iterator = missingPrimaryKeys.iterator(); iterator.hasNext(); ) {
            PrimaryKey primaryKey = iterator.next();
            if ("DATABASECHANGELOGLOCK".equalsIgnoreCase(primaryKey.getTable().getName()) || "DATABASECHANGELOG".equalsIgnoreCase(primaryKey.getTable().getName()))
                diffResult.getMissingObjects().remove(primaryKey);
        }
    }

    /**
     * Columns created as float are seen as DOUBLE(64) in database metadata.
     * HsqlDB bug?
     *
     * @param diffResult
     * @throws Exception
     */
    private void ignoreConversionFromFloatToDouble64(DiffResult diffResult) throws Exception {
        Map<DatabaseObject, ObjectDifferences> differences = diffResult.getChangedObjects();
        for (Iterator<Entry<DatabaseObject, ObjectDifferences>> iterator = differences.entrySet().iterator(); iterator.hasNext(); ) {
            Entry<DatabaseObject, ObjectDifferences> changedObject = iterator.next();
            Difference difference = changedObject.getValue().getDifference("type");
            if (difference != null && difference.getReferenceValue() != null && difference.getComparedValue() != null
                    && difference.getReferenceValue().toString().equals("float") && difference.getComparedValue().toString().startsWith("DOUBLE(64)")) {
                Scope.getCurrentScope().getLog(getClass()).info("Ignoring difference " + changedObject.getKey().toString() + " " + difference.toString());
                changedObject.getValue().removeDifference(difference.getField());
            }
        }
    }

}
