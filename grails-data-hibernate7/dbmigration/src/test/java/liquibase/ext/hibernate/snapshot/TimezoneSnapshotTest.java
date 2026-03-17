package liquibase.ext.hibernate.snapshot;

import liquibase.CatalogAndSchema;
import liquibase.database.Database;
import liquibase.integration.commandline.CommandLineUtils;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.SnapshotControl;
import liquibase.snapshot.SnapshotGeneratorFactory;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.Column;
import liquibase.structure.core.DataType;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class TimezoneSnapshotTest {

    @Test
    public void testTimezoneColumns() throws Exception {
        Database database = CommandLineUtils.createDatabaseObject(new ClassLoaderResourceAccessor(this.getClass().getClassLoader()), "hibernate:spring:com.example.timezone?dialect=org.hibernate.dialect.H2Dialect", null, null, null, null, null, false, false, null, null, null, null, null, null, null);

        DatabaseSnapshot snapshot = SnapshotGeneratorFactory.getInstance().createSnapshot(CatalogAndSchema.DEFAULT, database, new SnapshotControl(database));

        assertThat(
                snapshot.get(Column.class),
                hasItems(
                        // Instant column should result in 'timestamp with timezone' type
                        allOf(
                                hasProperty("name", equalTo("timestamp1")),
                                hasDatabaseAttribute("type", DataType.class, hasProperty("typeName", equalTo("timestamp with timezone")))
                        ),
                        // LocalDateTime column should result in 'timestamp' type
                        allOf(
                                hasProperty("name", equalTo("timestamp2")),
                                hasDatabaseAttribute("type", DataType.class, hasProperty("typeName", equalTo("timestamp")))
                        ),
                        // Instant column with explicit definition 'timestamp' should result in 'timestamp' type
                        allOf(
                                hasProperty("name", equalTo("timestamp3")),
                                hasDatabaseAttribute("type", DataType.class, hasProperty("typeName", equalTo("timestamp")))
                        ),
                        // LocalDateTime Colum with explicit definition 'TIMESTAMP WITH TIME ZONE' should result in 'TIMESTAMP with timezone' type
                        allOf(
                                hasProperty("name", equalTo("timestamp4")),
                                hasDatabaseAttribute("type", DataType.class, hasProperty("typeName", equalToIgnoringCase("timestamp with timezone")))
                        )
                )
        );
    }

    private static <T> FeatureMatcher<DatabaseObject, T> hasDatabaseAttribute(String attribute, Class<T> type, Matcher<T> matcher) {
        return new FeatureMatcher<>(matcher, attribute, attribute) {

            @Override
            protected T featureValueOf(DatabaseObject databaseObject) {
                return databaseObject.getAttribute(attribute, type);
            }

        };
    }

}
