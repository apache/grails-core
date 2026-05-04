package liquibase.ext.hibernate.snapshot.extension;

import liquibase.exception.UnexpectedLiquibaseException;
import liquibase.structure.core.Column;
import liquibase.structure.core.DataType;
import liquibase.structure.core.Table;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.id.MultipleHiLoPerTableGenerator;

import java.lang.reflect.Field;

public class MultipleHiLoPerTableSnapshotGenerator implements ExtendedSnapshotGenerator<IdentifierGenerator, Table> {

    private static final String TABLE_NAME = "tableName";
    private static final String PK_COLUMN_NAME = "pkColumnName";
    private static final String VALUE_COLUMN_NAME = "valueColumnName";
    private static final String KEY_SIZE = "keySize";

    private static final String PK_DATA_TYPE = "varchar";
    private static final String VALUE_DATA_TYPE = "integer";

    @Override
    public Table snapshot(IdentifierGenerator ig) {
        Table table;
        try {
            MultipleHiLoPerTableGenerator tableGenerator = (MultipleHiLoPerTableGenerator) ig;
            Class<? extends MultipleHiLoPerTableGenerator> aClass = tableGenerator.getClass();

            Field tableName = aClass.getDeclaredField(TABLE_NAME);
            tableName.setAccessible(true);
            Field pkColumnName = aClass.getDeclaredField(PK_COLUMN_NAME);
            pkColumnName.setAccessible(true);
            Field valueColumnName = aClass.getDeclaredField(VALUE_COLUMN_NAME);
            valueColumnName.setAccessible(true);
            Field keySize = aClass.getDeclaredField(KEY_SIZE);
            keySize.setAccessible(true);

            table = new Table().setName((String) tableName.get(tableGenerator));

            Column pkColumn = new Column();
            pkColumn.setName((String) pkColumnName.get(tableGenerator));
            DataType pkDataType = new DataType(PK_DATA_TYPE);
            pkDataType.setColumnSize(keySize.getInt(tableGenerator));
            pkColumn.setType(pkDataType);
            pkColumn.setCertainDataType(false);
            pkColumn.setRelation(table);
            table.getColumns().add(pkColumn);

            Column valueColumn = new Column();
            valueColumn.setName((String) valueColumnName.get(tableGenerator));
            valueColumn.setType(new DataType(VALUE_DATA_TYPE));
            valueColumn.setCertainDataType(false);
            valueColumn.setRelation(table);
            table.getColumns().add(valueColumn);

        } catch (Exception e) {
            throw new UnexpectedLiquibaseException(e);
        }
        return table;
    }

    @Override
    public boolean supports(IdentifierGenerator ig) {
        return ig instanceof MultipleHiLoPerTableGenerator;
    }

}
