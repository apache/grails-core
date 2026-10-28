package liquibase.ext.hibernate;

import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.core.H2Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.diff.DiffResult;
import liquibase.diff.compare.CompareControl;
import liquibase.diff.output.DiffOutputControl;
import liquibase.diff.output.changelog.DiffToChangeLog;
import liquibase.ext.hibernate.database.HibernateSpringPackageDatabase;
import liquibase.ext.hibernate.database.connection.HibernateConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.Table;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.HashSet;
import java.util.Set;

import static junit.framework.TestCase.assertTrue;

/**
 * Verifies that diffChangelog produces a setColumnRemarks changeset when
 * an existing DB column gains a @Comment in the Hibernate entity.
 */
public class ColumnRemarksIntegrationTest {

    private Database database;
    private Connection connection;

    @Before
    public void setUp() throws Exception {
        Class.forName("org.h2.Driver");
        connection = DriverManager.getConnection("jdbc:h2:mem:REMARKS_TEST" + System.currentTimeMillis(), "SA", "");
        database = new H2Database();
        database.setConnection(new JdbcConnection(connection));

        // Create the table manually without any column comment — simulates an existing pre-comment DB
        connection.createStatement().execute(
                "CREATE TABLE ITEM (ID BIGINT PRIMARY KEY, NAME VARCHAR(255), DESCRIPTION VARCHAR(255))");
    }

    @After
    public void tearDown() throws Exception {
        database.close();
        connection = null;
        database = null;
    }

    @Test
    public void diffProducesSetColumnRemarksForAnnotatedColumn() throws Exception {
        Database hibernateDatabase = new HibernateSpringPackageDatabase();
        hibernateDatabase.setConnection(new JdbcConnection(new HibernateConnection(
                "hibernate:spring:com.example.columnremarks?dialect=org.hibernate.dialect.H2Dialect",
                new ClassLoaderResourceAccessor())));

        Set<Class<? extends DatabaseObject>> typesToInclude = new HashSet<>();
        typesToInclude.add(Table.class);
        typesToInclude.add(Column.class);
        CompareControl compareControl = new CompareControl(typesToInclude);

        Liquibase liquibase = new Liquibase((String) null, new ClassLoaderResourceAccessor(), database);
        DiffResult diffResult = liquibase.diff(hibernateDatabase, database, compareControl);

        String changeLog = toChangeLog(diffResult);

        assertTrue("Expected setColumnRemarks changeset for 'name' column",
                changeLog.contains("setColumnRemarks"));
        assertTrue("Expected remark text for 'name' column",
                changeLog.contains("The name of the item"));
    }

    private String toChangeLog(DiffResult diffResult) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PrintStream printStream = new PrintStream(out, true, StandardCharsets.UTF_8);
        DiffOutputControl diffOutputControl = new DiffOutputControl();
        diffOutputControl.setIncludeCatalog(false);
        diffOutputControl.setIncludeSchema(false);
        DiffToChangeLog diffToChangeLog = new DiffToChangeLog(diffResult, diffOutputControl);
        diffToChangeLog.print(printStream);
        printStream.close();
        return out.toString(StandardCharsets.UTF_8);
    }
}
