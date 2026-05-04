package liquibase.ext.hibernate.snapshot;

import liquibase.exception.DatabaseException;
import liquibase.structure.core.DataType;
import org.junit.Test;

import java.sql.Types;

import static org.junit.Assert.*;

public class ColumnSnapshotGeneratorTest {

    @Test
    public void toDataType() throws DatabaseException {
        ColumnSnapshotGenerator columnSnapshotGenerator = new ColumnSnapshotGenerator();
        DataType varchar = columnSnapshotGenerator.toDataType("varchar(255)", Types.VARCHAR);
        assertEquals("varchar", varchar.getTypeName());
        assertEquals(255, varchar.getColumnSize().intValue());
        assertEquals(Types.VARCHAR, varchar.getDataTypeId().intValue());
        assertNull(varchar.getColumnSizeUnit());

        DataType intType = columnSnapshotGenerator.toDataType("integer", Types.INTEGER);
        assertEquals("integer", intType.getTypeName());

        DataType varcharChar = columnSnapshotGenerator.toDataType("varchar2(30 char)", Types.INTEGER);
        assertEquals("varchar2", varcharChar.getTypeName());
        assertEquals(30, varcharChar.getColumnSize().intValue());
        assertEquals(DataType.ColumnSizeUnit.CHAR, varcharChar.getColumnSizeUnit());

    }
}
