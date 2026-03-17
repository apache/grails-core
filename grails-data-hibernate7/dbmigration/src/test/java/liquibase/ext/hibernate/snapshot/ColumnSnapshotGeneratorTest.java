package liquibase.ext.hibernate.snapshot;

import liquibase.exception.DatabaseException;
import liquibase.structure.core.DataType;
import org.hibernate.type.SqlTypes;
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


        DataType enumType = columnSnapshotGenerator.toDataType("enum ('a', 'b', 'c')", SqlTypes.ENUM);
        assertEquals("enum ('a', 'b', 'c')", enumType.getTypeName());
        assertNull(enumType.getColumnSize());
        assertEquals(SqlTypes.ENUM, enumType.getDataTypeId().intValue());
        assertNull(enumType.getColumnSizeUnit());

        DataType timestampWithTz = columnSnapshotGenerator.toDataType("timestamp with time zone", Types.TIMESTAMP_WITH_TIMEZONE);
        assertEquals("timestamp with timezone", timestampWithTz.getTypeName());
        assertNull(timestampWithTz.getColumnSize());

        DataType timestamp6WithTz = columnSnapshotGenerator.toDataType("timestamp(6) with time zone", Types.TIMESTAMP_WITH_TIMEZONE);
        assertEquals("timestamp with timezone", timestamp6WithTz.getTypeName());
        assertEquals(6, timestamp6WithTz.getColumnSize().intValue());

        DataType decimalType = columnSnapshotGenerator.toDataType("decimal(10,2)", Types.DECIMAL);
        assertEquals("decimal", decimalType.getTypeName());
        assertEquals(10, decimalType.getColumnSize().intValue());
        assertEquals(2, decimalType.getDecimalDigits().intValue());

        DataType numericType = columnSnapshotGenerator.toDataType("numeric(10)", Types.NUMERIC);
        assertEquals("numeric", numericType.getTypeName());
        assertEquals(10, numericType.getColumnSize().intValue());
        assertNull(numericType.getDecimalDigits());

        DataType bitType = columnSnapshotGenerator.toDataType("bit(1)", Types.BIT);
        assertEquals("bit", bitType.getTypeName());
        assertEquals(1, bitType.getColumnSize().intValue());

        DataType nvarcharType = columnSnapshotGenerator.toDataType("nvarchar2(255)", Types.NVARCHAR);
        assertEquals("nvarchar2", nvarcharType.getTypeName());
        assertEquals(255, nvarcharType.getColumnSize().intValue());

        DataType charType = columnSnapshotGenerator.toDataType("char(10)", Types.CHAR);
        assertEquals("char", charType.getTypeName());
        assertEquals(10, charType.getColumnSize().intValue());

        DataType nvarcharMax = columnSnapshotGenerator.toDataType("nvarchar(max)", Types.NVARCHAR);
        assertEquals("nvarchar max", nvarcharMax.getTypeName());
        assertNull(nvarcharMax.getColumnSize());

        DataType decimalUnsigned = columnSnapshotGenerator.toDataType("decimal(10,2) unsigned", Types.DECIMAL);
        assertEquals("decimal unsigned", decimalUnsigned.getTypeName().toLowerCase());

        DataType doublePrecision = columnSnapshotGenerator.toDataType("double precision", Types.DOUBLE);
        assertEquals("double precision", doublePrecision.getTypeName().toLowerCase());

        DataType varcharCollate = columnSnapshotGenerator.toDataType("varchar(255) collate utf8_bin", Types.VARCHAR);
        assertEquals("varchar collate utf8_bin", varcharCollate.getTypeName().toLowerCase());
        assertEquals(255, varcharCollate.getColumnSize().intValue());

        DataType int11 = columnSnapshotGenerator.toDataType("int(11)", Types.INTEGER);
        assertEquals("int", int11.getTypeName().toLowerCase());
        assertEquals(11, int11.getColumnSize().intValue());

        DataType varcharBinary = columnSnapshotGenerator.toDataType("varchar(255) binary", Types.VARCHAR);
        assertEquals("varchar binary", varcharBinary.getTypeName().toLowerCase());
        assertEquals(255, varcharBinary.getColumnSize().intValue());

        DataType datetime6 = columnSnapshotGenerator.toDataType("datetime(6)", Types.TIMESTAMP);
        assertEquals("datetime", datetime6.getTypeName().toLowerCase());
        assertEquals(6, datetime6.getColumnSize().intValue());
    }
}
