package grails.gorm.hibernate.mapping

import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.HibernateMappingBuilder

/**
 * Created by graemerocher on 01/02/2017.
 */

import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.FetchMode
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertThrows
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Tests that the Hibernate mapping DSL constructs a valid Mapping object.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
class HibernateMappingBuilderTests {

//    void testWildcardApplyToAllProperties() {
//        def builder = new HibernateMappingBuilder("Foo")
//        def mapping = builder.evaluate {
//            '*'(column:"foo")
//            '*-1'(column:"foo")
//            '1-1'(column:"foo")
//            '1-*'(column:"foo")
//            '*-*'(column:"foo")
//            one cache:true
//            two ignoreNoteFound:false
//        }
//    }

    @Test
    void testIncludes() {
        def callable = {
            foos lazy:false
        }
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            includes callable
            foos ignoreNotFound:true
        }

        def pc = mapping.getPropertyConfig("foos")
        assert pc.ignoreNotFound : "should have ignoreNotFound enabled"
        assert !pc.lazy : "should not be lazy"
    }

    @Test
    void testIgnoreNotFound() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            foos ignoreNotFound:true
        }

        assertTrue mapping.getPropertyConfig("foos").ignoreNotFound, "ignore not found should have been true"

        mapping = builder.evaluate {
            foos ignoreNotFound:false
        }
        assertFalse mapping.getPropertyConfig("foos").ignoreNotFound, "ignore not found should have been false"

        mapping = builder.evaluate { // default
            foos lazy:false
        }
        assertFalse mapping.getPropertyConfig("foos").ignoreNotFound, "ignore not found should have been false"
    }

    @Test
    void testNaturalId() {

        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            id natural: 'one'
        }

        assertEquals(['one'], mapping.identity.natural.propertyNames)

        mapping = builder.evaluate {
            id natural: ['one','two']
        }

        assertEquals(['one','two'], mapping.identity.natural.propertyNames)

        mapping = builder.evaluate {
            id natural: [properties:['one','two'], mutable:true]
        }

        assertEquals(['one','two'], mapping.identity.natural.propertyNames)
        assertTrue mapping.identity.natural.mutable
    }

    @Test
    void testDiscriminator() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            discriminator 'one'
        }

        assertEquals "one", mapping.discriminator.value
        assertNull mapping.discriminator.column

        mapping = builder.evaluate {
            discriminator value:'one', column:'type'
        }

        assertEquals "one", mapping.discriminator.value
        assertEquals "type", mapping.discriminator.column.name

        mapping = builder.evaluate {
            discriminator value:'one', column:[name:'type', sqlType:'integer']
        }

        assertEquals "one", mapping.discriminator.value
        assertEquals "type", mapping.discriminator.column.name
        assertEquals "integer", mapping.discriminator.column.sqlType
    }

    @Test
    void testDiscriminatorMap() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            discriminator value:'1', formula:"case when CLASS_TYPE in ('a', 'b', 'c') then 0 else 1 end",type:'integer',insert:false
        }

        assertEquals "1", mapping.discriminator.value
        assertNull mapping.discriminator.column

        assertEquals "case when CLASS_TYPE in ('a', 'b', 'c') then 0 else 1 end", mapping.discriminator.formula
        assertEquals "integer", mapping.discriminator.type
        assertFalse mapping.discriminator.insertable
    }

    @Test
    void testAutoImport() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate { }

        assertTrue mapping.autoImport, "default auto-import should be true"

        mapping = builder.evaluate {
            autoImport false
        }

        assertFalse mapping.autoImport, "auto-import should be false"
    }

    @Test
    void testTableWithCatalogueAndSchema() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table name:"table", catalog:"CRM", schema:"dbo"
        }

        assertEquals 'table',mapping.table.name
        assertEquals 'dbo',mapping.table.schema
        assertEquals 'CRM',mapping.table.catalog
    }

    @Test
    void testIndexColumn() {

        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            things indexColumn:[name:"chapter_number", type:"string", length:3]
        }

        PropertyConfig pc = mapping.getPropertyConfig("things")
        assertEquals "chapter_number",pc.indexColumn.column
        assertEquals "string",pc.indexColumn.type
        assertEquals 3, pc.indexColumn.length
    }

    @Test
    void testDynamicUpdate() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            dynamicUpdate true
            dynamicInsert true
        }

        assertTrue mapping.dynamicUpdate
        assertTrue mapping.dynamicInsert

        builder = new HibernateMappingBuilder("Foo")
        mapping = builder.evaluate {}

        assertFalse mapping.dynamicUpdate
        assertFalse mapping.dynamicInsert
    }

    @Test
    void testBatchSizeConfig() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            batchSize 10
            things batchSize:15
        }

        assertEquals 10, mapping.batchSize
        assertEquals 15,mapping.getPropertyConfig('things').batchSize
    }

    @Test
    void testChangeVersionColumn() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            version 'v_number'
        }

        assertEquals 'v_number', mapping.getPropertyConfig("version").column
    }

    @Test
    void testClassSortOrder() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            sort "name"
            order "desc"
            columns {
                things sort:'name'
            }
        }

        assertEquals "name", mapping.sort.name
        assertEquals "desc", mapping.sort.direction
        assertEquals 'name',mapping.getPropertyConfig('things').sort

        mapping = builder.evaluate {
            sort name:'desc'

            columns {
                things sort:'name'
            }
        }

        assertEquals "name", mapping.sort.name
        assertEquals "desc", mapping.sort.direction
        assertEquals 'name',mapping.getPropertyConfig('things').sort
    }

    @Test
    void testAssociationSortOrder() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            columns {
                things sort:'name'
            }
        }

        assertEquals 'name',mapping.getPropertyConfig('things').sort
    }

    @Test
    void testLazy() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            columns {
                things cascade:'save-update'
            }
        }

        assertNull mapping.getPropertyConfig('things').getLazy(), "should have been lazy"

        mapping = builder.evaluate {
            columns {
                things lazy:false
            }
        }

        assertFalse mapping.getPropertyConfig('things').lazy, "shouldn't have been lazy"
    }

    @Test
    void testCascades() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            columns {
                things cascade:'save-update'
            }
        }

        assertEquals 'save-update',mapping.getPropertyConfig('things').cascade
    }

    @Test
    void testFetchModes() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            columns {
                things fetch:'join'
                others fetch:'select'
                mores column:'yuck'
            }
        }

        assertEquals FetchMode.JOIN,mapping.getPropertyConfig('things').fetchMode
        assertEquals FetchMode.SELECT,mapping.getPropertyConfig('others').fetchMode
        assertEquals FetchMode.DEFAULT,mapping.getPropertyConfig('mores').fetchMode
    }

    @Test
    void testEnumType() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            columns {
                things column:'foo'
            }
        }

        assertEquals 'default',mapping.getPropertyConfig('things').enumType

        mapping = builder.evaluate {
            columns {
                things enumType:'ordinal'
            }
        }

        assertEquals 'ordinal',mapping.getPropertyConfig('things').enumType
    }

    @Test
    void testCascadesWithColumnsBlock() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            things cascade:'save-update'
        }
        assertEquals 'save-update',mapping.getPropertyConfig('things').cascade
    }

    @Test
    void testJoinTableMapping() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            columns {
                things joinTable:true
            }
        }

        assert mapping.getPropertyConfig('things')?.joinTable

        mapping = builder.evaluate {
            columns {
                things joinTable:'foo'
            }
        }

        PropertyConfig property = mapping.getPropertyConfig('things')
        assert property?.joinTable
        assertEquals "foo", property.joinTable.name

        mapping = builder.evaluate {
            columns {
                things joinTable:[name:'foo', key:'foo_id', column:'bar_id']
            }
        }

        property = mapping.getPropertyConfig('things')
        assert property?.joinTable
        assertEquals "foo", property.joinTable.name
        assertEquals "foo_id", property.joinTable.key.name
        assertEquals "bar_id", property.joinTable.column.name
    }

    @Test
    void testJoinTableMappingWithoutColumnsBlock() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            things joinTable:true
        }

        assert mapping.getPropertyConfig('things')?.joinTable

        mapping = builder.evaluate {
            things joinTable:'foo'
        }

        PropertyConfig property = mapping.getPropertyConfig('things')
        assert property?.joinTable
        assertEquals "foo", property.joinTable.name

        mapping = builder.evaluate {
            things joinTable:[name:'foo', key:'foo_id', column:'bar_id']
        }

        property = mapping.getPropertyConfig('things')
        assert property?.joinTable
        assertEquals "foo", property.joinTable.name
        assertEquals "foo_id", property.joinTable.key.name
        assertEquals "bar_id", property.joinTable.column.name
    }

    @Test
    void testCustomInheritanceStrategy() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            tablePerHierarchy false
        }

        assertFalse mapping.tablePerHierarchy

        mapping = builder.evaluate {
            table 'myTable'
            tablePerSubclass true
        }

        assertFalse mapping.tablePerHierarchy
    }

    @Test
    void testAutoTimeStamp() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            autoTimestamp false
        }

        assertFalse mapping.autoTimestamp
    }

    @Test
    void testCustomAssociationCachingConfig1() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            columns {
                firstName cache:[usage:'read-only', include:'non-lazy']
            }
        }

        def cc = mapping.getPropertyConfig('firstName')
        assertEquals 'read-only', cc.cache.usage
        assertEquals 'non-lazy', cc.cache.include
    }

    @Test
    void testCustomAssociationCachingConfig1WithoutColumnsBlock() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            firstName cache:[usage:'read-only', include:'non-lazy']
        }

        def cc = mapping.getPropertyConfig('firstName')
        assertEquals 'read-only', cc.cache.usage
        assertEquals 'non-lazy', cc.cache.include
    }

    @Test
    void testCustomAssociationCachingConfig2() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'

            columns {
                firstName cache:'read-only'
            }
        }

        def cc = mapping.getPropertyConfig('firstName')
        assertEquals 'read-only', cc.cache.usage
    }

    @Test
    void testCustomAssociationCachingConfig2WithoutColumnsBlock() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            firstName cache:'read-only'
        }

        def cc = mapping.getPropertyConfig('firstName')
        assertEquals 'read-only', cc.cache.usage
    }

    @Test
    void testAssociationCachingConfig() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'

            columns {
                firstName cache:true
            }
        }

        def cc = mapping.getPropertyConfig('firstName')
        assertEquals 'read-write', cc.cache.usage
        assertEquals 'all', cc.cache.include
    }

    @Test
    void testAssociationCachingConfigWithoutColumnsBlock() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            firstName cache:true
        }

        def cc = mapping.getPropertyConfig('firstName')
        assertEquals 'read-write', cc.cache.usage
        assertEquals 'all', cc.cache.include
    }

    @Test
    void testEvaluateTableName() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
        }

        assertEquals 'myTable', mapping.tableName
    }

    @Test
    void testDefaultCacheStrategy() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            cache true
        }

        assertEquals 'read-write', mapping.cache.usage
        assertEquals 'all', mapping.cache.include
    }

    @Test
    void testCustomCacheStrategy() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            cache usage:'read-only', include:'non-lazy'
        }

        assertEquals 'read-only', mapping.cache.usage
        assertEquals 'non-lazy', mapping.cache.include
    }

    @Test
    void testCustomCacheStrategy2() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            cache 'read-only'
        }

        assertEquals 'read-only', mapping.cache.usage
        assertEquals 'all', mapping.cache.include
    }

    @Test
    void testInvalidCacheValues() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            cache usage:'rubbish', include:'more-rubbish'
        }

        // should be ignored and logged to console
        assertEquals 'read-write', mapping.cache.usage
        assertEquals 'all', mapping.cache.include
    }

    @Test
    void testEvaluateVersioning() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            version false
        }

        assertEquals 'myTable', mapping.tableName
        assertFalse mapping.versioned
    }

    @Test
    void testIdentityColumnMapping() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            version false
            id column:'foo_id', type:Integer
        }

        assertEquals Long, mapping.identity.type
        assertEquals 'foo_id', mapping.getPropertyConfig("id").column
        assertEquals Integer, mapping.getPropertyConfig("id").type
        assertEquals 'native', mapping.identity.generator
    }

    @Test
    void testDefaultIdStrategy() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            version false
        }

        assertEquals Long, mapping.identity.type
        assertEquals 'id', mapping.identity.column
        assertEquals 'native', mapping.identity.generator
    }

    @Test
    void testHiloIdStrategy() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            version false
            id generator:'hilo', params:[table:'hi_value',column:'next_value',max_lo:100]
        }

        assertEquals Long, mapping.identity.type
        assertEquals 'id', mapping.identity.column
        assertEquals 'hilo', mapping.identity.generator
        assertEquals 'hi_value', mapping.identity.params.table
    }

    @Test
    void testCompositeIdStrategy() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            version false
            id composite:['one','two'], compositeClass:HibernateMappingBuilder
        }

        assert mapping.identity instanceof CompositeIdentity
        assertEquals "one", mapping.identity.propertyNames[0]
        assertEquals "two", mapping.identity.propertyNames[1]
        assertEquals HibernateMappingBuilder, mapping.identity.compositeClass
    }

    @Test
    void testSimpleColumnMappingsWithoutColumnsBlock() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            version false
            firstName column:'First_Name'
            lastName column:'Last_Name'
        }

        assertEquals "First_Name",mapping.getPropertyConfig('firstName').column
        assertEquals "Last_Name",mapping.getPropertyConfig('lastName').column
    }

    @Test
    void testSimpleColumnMappings() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            version false
            columns {
                firstName column:'First_Name'
                lastName column:'Last_Name'
            }
        }

        assertEquals "First_Name",mapping.getPropertyConfig('firstName').column
        assertEquals "Last_Name",mapping.getPropertyConfig('lastName').column
    }

    @Test
    void testComplexColumnMappings() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            version false
            columns {
                firstName  column:'First_Name',
                        lazy:true,
                        unique:true,
                        type: java.sql.Clob,
                        length:255,
                        index:'foo',
                        sqlType: 'text'

                lastName column:'Last_Name'
            }
        }

        assertEquals "First_Name",mapping.columns.firstName.column
        assertTrue mapping.columns.firstName.lazy
        assertTrue mapping.columns.firstName.unique
        assertEquals java.sql.Clob,mapping.columns.firstName.type
        assertEquals 255,mapping.columns.firstName.length
        assertEquals 'foo',mapping.columns.firstName.getIndexName()
        assertEquals "text",mapping.columns.firstName.sqlType
        assertEquals "Last_Name",mapping.columns.lastName.column
    }

    @Test
    void testComplexColumnMappingsWithoutColumnsBlock() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            table 'myTable'
            version false
            firstName  column:'First_Name',
                    lazy:true,
                    unique:true,
                    type: java.sql.Clob,
                    length:255,
                    index:'foo',
                    sqlType: 'text'

            lastName column:'Last_Name'
        }

        assertEquals "First_Name",mapping.columns.firstName.column
        assertTrue mapping.columns.firstName.lazy
        assertTrue mapping.columns.firstName.unique
        assertEquals java.sql.Clob,mapping.columns.firstName.type
        assertEquals 255,mapping.columns.firstName.length
        assertEquals 'foo',mapping.columns.firstName.getIndexName()
        assertEquals "text",mapping.columns.firstName.sqlType
        assertEquals "Last_Name",mapping.columns.lastName.column
    }

    @Test
    void testPropertyWithMultipleColumns() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            amount type: MyUserType, {
                column name: "value"
                column name: "currency", sqlType: "char", length: 3
            }
        }

        assertEquals 2, mapping.columns.amount.columns.size()
        assertEquals "value", mapping.columns.amount.columns[0].name
        assertEquals "currency", mapping.columns.amount.columns[1].name
        assertEquals "char", mapping.columns.amount.columns[1].sqlType
        assertEquals 3, mapping.columns.amount.columns[1].length

        assertThrows Throwable, { mapping.columns.amount.column }
        assertThrows Throwable, { mapping.columns.amount.sqlType }
    }

    @Test
    void testConstrainedPropertyWithMultipleColumns() {
        def builder = new HibernateMappingBuilder("Foo")
        builder.evaluate {
            amount type: MyUserType, {
                column name: "value"
                column name: "currency", sqlType: "char", length: 3
            }
        }
        def mapping = builder.evaluate {
            amount nullable: true
        }

        assertEquals 2, mapping.columns.amount.columns.size()
        assertEquals "value", mapping.columns.amount.columns[0].name
        assertEquals "currency", mapping.columns.amount.columns[1].name
        assertEquals "char", mapping.columns.amount.columns[1].sqlType
        assertEquals 3, mapping.columns.amount.columns[1].length

        assertThrows Throwable, { mapping.columns.amount.column }
        assertThrows Throwable, { mapping.columns.amount.sqlType }
    }

    @Test
    void testDisallowedConstrainedPropertyWithMultipleColumns() {
        def builder = new HibernateMappingBuilder("Foo")
        builder.evaluate {
            amount type: MyUserType, {
                column name: "value"
                column name: "currency", sqlType: "char", length: 3
            }
        }
        assertThrows(Throwable, {
            builder.evaluate {
                amount scale: 2
            }
        }, "Cannot treat multi-column property as a single-column property")
    }

    @Test
    void testPropertyWithUserTypeAndNoParams() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            amount type: MyUserType
        }

        assertEquals MyUserType, mapping.getPropertyConfig('amount').type
        assertNull mapping.getPropertyConfig('amount').typeParams
    }

    @Test
    void testPropertyWithUserTypeAndTypeParams() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            amount type: MyUserType, params : [ param1 : "amountParam1", param2 : 65 ]
            value type: MyUserType, params : [ param1 : "valueParam1", param2 : 21 ]
        }

        assertEquals MyUserType, mapping.getPropertyConfig('amount').type
        assertEquals "amountParam1", mapping.getPropertyConfig('amount').typeParams.param1
        assertEquals 65, mapping.getPropertyConfig('amount').typeParams.param2
        assertEquals MyUserType, mapping.getPropertyConfig('value').type
        assertEquals "valueParam1", mapping.getPropertyConfig('value').typeParams.param1
        assertEquals 21, mapping.getPropertyConfig('value').typeParams.param2
    }

    @Test
    void testInsertablePropertyConfig() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            firstName insertable:true
            lastName insertable:false
        }
        assertTrue mapping.getPropertyConfig('firstName').insertable
        assertFalse mapping.getPropertyConfig('lastName').insertable
    }

    @Test
    void testUpdatablePropertyConfig() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            firstName updateable:true
            lastName updateable:false
        }
        assertTrue mapping.getPropertyConfig('firstName').updateable
        assertFalse mapping.getPropertyConfig('lastName').updateable
    }

    @Test
    void testDefaultValue() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            comment 'wahoo'
            name comment: 'bar'
            foo defaultValue: '5'
        }
        assertEquals '5', mapping.getPropertyConfig('foo').columns[0].defaultValue
        assertNull mapping.getPropertyConfig('name').columns[0].defaultValue
    }

    @Test
    void testColumnComment() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            comment 'wahoo'
            name comment: 'bar'
            foo defaultValue: '5'
        }
        assertEquals 'bar', mapping.getPropertyConfig('name').columns[0].comment
        assertNull mapping.getPropertyConfig('foo').columns[0].comment
    }

    @Test
    void testTableComment() {
        def builder = new HibernateMappingBuilder("Foo")
        def mapping = builder.evaluate {
            comment 'wahoo'
            name comment: 'bar'
            foo defaultValue: '5'
        }
        assertEquals 'wahoo', mapping.comment
    }
    // dummy user type
    static class MyUserType {}
}
