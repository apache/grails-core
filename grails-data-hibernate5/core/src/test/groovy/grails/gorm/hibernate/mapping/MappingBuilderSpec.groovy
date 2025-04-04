package grails.gorm.hibernate.mapping

import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.orm.hibernate.cfg.CompositeIdentity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PropertyConfig
import spock.lang.Specification

import jakarta.persistence.FetchType

import static grails.gorm.hibernate.mapping.MappingBuilder.define
/**
 * Created by graemerocher on 01/02/2017.
 */
class MappingBuilderSpec extends Specification {

    void "test basic table mapping configuration"() {
        when:
        Mapping mapping = define {
            autowire false
            table "test"
        }.build()

        then:
        !mapping.autowire
        mapping.table.name == 'test'
    }

    void "test complex table mapping"() {
        given:
        Mapping mapping = define {
            table {
                catalog "foo"
                schema "bar"
                name "test"
            }
        }.build()

        expect:
        mapping.table.name == 'test'
        mapping.table.catalog == 'foo'
        mapping.table.schema == 'bar'
    }

    void "test id mapping"() {
        given:
        Mapping mapping = define {
            id {
                name 'test'
                generator 'native'
                params foo:'bar'
            }
        }.build()

        expect:
        mapping.identity.name == 'test'
        mapping.identity.generator == 'native'
        mapping.identity.params == [foo:'bar']
    }

    void "test composite id mapping"() {
        given:
        Mapping mapping = define {
            id composite("foo", "bar").compositeClass(MappingBuilderSpec)
        }.build()

        expect:
        mapping.identity instanceof CompositeIdentity
        mapping.identity.propertyNames == ['foo', 'bar']
        mapping.identity.compositeClass == MappingBuilderSpec
    }

    void "test cache mapping"() {
        given:
        Mapping mapping = define {
            cache {
                enabled true
                usage 'read'
                include 'some'
            }
        }.build()

        expect:
        mapping.cache.enabled
        mapping.cache.usage == 'read'
        mapping.cache.include == 'some'
    }

    void "test sort mapping"() {
        when:
        Mapping mapping = define {
            sort("foo", 'desc')
        }.build()
        then:
        mapping.sort.name == 'foo'
        mapping.sort.direction == 'desc'

        when:
        mapping = define {
            sort(foo:'bar')
        }.build()

        then:
        mapping.sort.namesAndDirections == [foo:'bar']
    }

    void "test simple discriminator mapping"() {
        given:
        Mapping mapping = define {
            discriminator "test"
        }.build()

        expect:
        mapping.discriminator != null
        mapping.discriminator.value == 'test'
        mapping.discriminator.column == null
        mapping.discriminator.insertable == null
    }

    void "test complex discriminator mapping"() {
        given:
        Mapping mapping = define {
            discriminator {
                value "test"
                column {
                    name "c_test"
                }
                insertable true
            }
        }.build()

        expect:
        mapping.discriminator != null
        mapping.discriminator.value == 'test'
        mapping.discriminator.column != null
        mapping.discriminator.column.name == 'c_test'
        mapping.discriminator.insertable
    }

    void "test simple alter version column"() {
        given:
        Mapping mapping = define {
            version "my_version"
        }.build()

        expect:
        mapping.getPropertyConfig(GormProperties.VERSION).column == "my_version"
    }

    void "test complex alter version column"() {
        given:
        Mapping mapping = define {
            version {
                type "int"
                column {
                    name 'my_version'
                    length 10
                }
            }
        }.build()
        PropertyConfig pc = mapping.getPropertyConfig(GormProperties.VERSION)
        expect:
        pc != null
        pc.columns.size() == 1
        pc.type == 'int'
        pc.columns[0].length == 10
        pc.column == "my_version"
    }

    void "test alter property config using property method"() {
        given:
        Mapping mapping = define {
            property('blah') {
                nullable true
                column {
                    defaultValue 'test'
                }
            }
        }.build()
        PropertyConfig config = mapping.getPropertyConfig('blah')

        expect:
        config != null
        config.nullable
        config.columns
        config.columns[0].defaultValue == 'test'
    }

    void "test alter property config using method missing"() {
        given:
        Mapping mapping = define {
            blah = property {
                nullable true
                column {
                    defaultValue 'test'
                }
            }
        }.build()
        PropertyConfig config = mapping.getPropertyConfig('blah')

        expect:
        config != null
        config.nullable
        config.columns
        config.columns[0].defaultValue == 'test'
    }

    void "test alter property config using map"() {
        given:
        Mapping mapping = define {
            blah nullable: true,{
                column {
                    defaultValue 'test'
                }
            }
        }.build()
        PropertyConfig config = mapping.getPropertyConfig('blah')

        expect:
        config != null
        config.nullable
        config.columns
        config.columns[0].defaultValue == 'test'
    }

    void "test configure join table mapping with closure"() {
        given:
        Mapping mapping = define {
            blah = property {
                joinTable {
                    name "foo"
                    key "foo_id"
                    column "bar_id"
                }
            }
        }.build()

        PropertyConfig config = mapping.getPropertyConfig('blah')

        expect:
        config != null
        config.joinTable != null
        config.joinTable.name == 'foo'
        config.joinTable.key.name == 'foo_id'
        config.joinTable.column.name == 'bar_id'

    }

    void "test configure join table mapping with map"() {
        given:
        Mapping mapping = define {
            blah = property {
                joinTable name: "foo",
                            key: "foo_id",
                            column: "bar_id"
            }
        }.build()

        PropertyConfig config = mapping.getPropertyConfig('blah')

        expect:
        config != null
        config.joinTable != null
        config.joinTable.name == 'foo'
        config.joinTable.key.name == 'foo_id'
        config.joinTable.column.name == 'bar_id'

    }

    void "test column config via map"() {
        given:
        Mapping mapping = define {
            table 'myTable'
            version false
            firstName   column:'First_Name',
                        lazy:true,
                        unique:true,
                        type: java.sql.Clob,
                        length:255,
                        index:'foo',
                        sqlType: 'text'

            property('lastName', [column:'Last_Name'])
        }.build()

        expect:
        "First_Name" == mapping.columns.firstName.column
        mapping.columns.firstName.lazy
        mapping.columns.firstName.unique
        java.sql.Clob == mapping.columns.firstName.type
        255 == mapping.columns.firstName.length
        'foo' == mapping.columns.firstName.getIndexName()
        "text" == mapping.columns.firstName.sqlType
        "Last_Name" ==  mapping.columns.lastName.column
    }

    void "test global mapping handling"() {
        given:
        Mapping mapping = define {
            '*'(property {
                column {
                    sqlType "text"
                }
            })
            firstName(property({
                column {
                    name "test"
                }
            }))
        }.build()

        expect:
        mapping.getPropertyConfig('*').sqlType == 'text'
        mapping.getPropertyConfig('firstName').sqlType == 'text'
        mapping.getPropertyConfig('firstName').column == 'test'
    }
}
