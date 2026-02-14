package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig

import org.hibernate.mapping.Column
import org.hibernate.mapping.SimpleValue
import spock.lang.Specification

import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder

class SimpleValueBinderSpec extends Specification {

    abstract static class TestTenantId extends TenantId<PropertyConfig> implements GrailsHibernatePersistentProperty {
        TestTenantId(PersistentEntity owner, MappingContext context, String name, Class type) {
            super(owner, context, name, type)
        }
    }

    def namingStrategy = Mock(PersistentEntityNamingStrategy)
    def columnConfigToColumnBinder = Mock(ColumnConfigToColumnBinder)
    def columnBinder = Mock(ColumnBinder)
    def jdbcEnvironment = Mock(org.hibernate.engine.jdbc.env.spi.JdbcEnvironment)
    def grailsSequenceWrapper = Mock(org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapper)

    def binder = new SimpleValueBinder(namingStrategy,
            columnConfigToColumnBinder,
            columnBinder,
            jdbcEnvironment,
            grailsSequenceWrapper)

    def "sets type from provider when present and applies type params"() {
        given:
        def prop = Mock(GrailsHibernatePersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def sv = Mock(SimpleValue)
        sv.getTable() >> null
        def props = new Properties(); props.setProperty('p1','v1')

        // stubs
        prop.getMappedForm() >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        prop.getTypeName() >> "custom.Type"
        pc.getTypeParams() >> props
        pc.isDerived() >> false
        pc.getColumns() >> null
        prop.getType() >> String
        prop.isNullable() >> true

        when:
        binder.bindSimpleValue(prop, null, sv, "p")

        then:
        1 * prop.getTypeName(sv) >> "custom.Type"
        1 * prop.getTypeParameters(sv) >> props
        1 * sv.setTypeName("custom.Type")
        1 * sv.setTypeParameters({ it.getProperty('p1') == 'v1' })
        1 * columnBinder.bindColumn(prop, null, _, null, 'p', null) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
        1 * columnConfigToColumnBinder.bindColumnConfigToColumn(_, null, pc) >> { args ->
            def column = args[0] as Column
            column.setName("testColumn")
        }
    }

    def "falls back to property type when provider returns null"() {
        given:
        def prop = Mock(GrailsHibernatePersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def sv = Mock(SimpleValue)
        sv.getTable() >> null

        prop.getMappedForm() >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        prop.getTypeName() >> null
        pc.isDerived() >> false
        pc.getColumns() >> null
        prop.getType() >> Integer
        prop.isNullable() >> true

        when:
        binder.bindSimpleValue(prop, null, sv, null)

        then:
        1 * prop.getTypeName(sv) >> Integer.name
        1 * prop.getTypeParameters(sv) >> null
        1 * sv.setTypeName(Integer.name)
        1 * columnBinder.bindColumn(prop, null, _, null, null, null) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
    }

    def "derived property adds no columns but adds formula, except TenantId"() {
        given:
        def prop = Mock(GrailsHibernatePersistentProperty)
        def tenantProp = Mock(TestTenantId)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def tenantPc = Mock(PropertyConfig)
        def sv = Mock(SimpleValue)
        def sv2 = Mock(SimpleValue)

        prop.getMappedForm() >> pc
        tenantProp.getMappedForm() >> tenantPc
        prop.getOwner() >> owner
        tenantProp.getOwner() >> owner
        owner.getMappedForm() >> mapping
        prop.getTypeName() >> 'X'

        pc.isDerived() >> true
        pc.getFormula() >> 'x+y'
        tenantPc.isDerived() >> true
        tenantPc.getFormula() >> 'ignored'

        when:
        binder.bindSimpleValue(prop, null, sv, null)

        then:
        1 * prop.getTypeName(sv) >> 'X'
        1 * prop.getTypeParameters(sv) >> null
        1 * sv.addFormula({ it.getFormula() == 'x+y' })
        0 * columnBinder.bindColumn(_, _, _, _, _, _)

        when:
        binder.bindSimpleValue(tenantProp, null, sv2, null)

        then:
        1 * tenantProp.getTypeName(sv2) >> 'X'
        1 * tenantProp.getTypeParameters(sv2) >> null
        0 * sv2.addFormula(_)
        1 * columnBinder.bindColumn(_, _, _, _, _, _) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
    }

    def "applies generator and maps sequence param to SequenceStyleGenerator.SEQUENCE_PARAM"() {
        given:
        def prop = Mock(GrailsHibernatePersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def sv = Mock(org.hibernate.mapping.BasicValue)
        sv.getTable() >> null
        def genProps = new Properties(); genProps.setProperty('sequence','seq_name'); genProps.setProperty('foo','bar')

        prop.getMappedForm() >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        prop.getTypeName() >> 'Y'
        pc.isDerived() >> false
        pc.getColumns() >> null
        pc.getGenerator() >> 'sequence'
        pc.getTypeParams() >> genProps
        prop.getType() >> String

        when:
        binder.bindSimpleValue(prop, null, sv, null)

        then:
        1 * prop.getTypeName(sv) >> 'Y'
        1 * prop.getTypeParameters(sv) >> null
        1 * columnBinder.bindColumn(prop, null, _, null, null, null) >> { args ->
            args[2].setName("testColumn")
        }
        1 * sv.setCustomIdGeneratorCreator(_)
    }

    def "binds for each provided column config and adds to table and simple value"() {
        given:
        def prop = Mock(GrailsHibernatePersistentProperty)
        def parent = Mock(GrailsHibernatePersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def cc1 = new ColumnConfig(name: 'c1')
        def cc2 = new ColumnConfig(name: 'c2')
        def sv = Mock(SimpleValue)
        sv.getTable() >> null

        prop.getMappedForm() >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        prop.getTypeName() >> 'Z'
        pc.isDerived() >> false
        pc.getColumns() >> [cc1, cc2]
        prop.isNullable() >> true
        parent.isNullable() >> false
        prop.getType() >> String

        when:
        binder.bindSimpleValue(prop, parent, sv, 'path')

        then:
        1 * prop.getTypeName(sv) >> 'Z'
        1 * prop.getTypeParameters(sv) >> null
        1 * columnConfigToColumnBinder.bindColumnConfigToColumn(_, cc1, pc) >> { args ->
            def column = args[0] as Column
            column.setName("testColumn")
        }
        1 * columnConfigToColumnBinder.bindColumnConfigToColumn(_, cc2, pc) >> { args ->
            def column = args[0] as Column
            column.setName("testColumn")
        }
        1 * columnBinder.bindColumn(prop, parent, _, cc1, 'path', null) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
        1 * columnBinder.bindColumn(prop, parent, _, cc2, 'path', null) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
        2 * sv.addColumn(_ as Column)
    }
}
