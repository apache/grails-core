package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.id.enhanced.SequenceStyleGenerator
import org.hibernate.mapping.Column
import org.hibernate.mapping.SimpleValue
import spock.lang.Specification

class SimpleValueBinderSpec extends Specification {

    def namingStrategy = Mock(PersistentEntityNamingStrategy)
    def columnConfigToColumnBinder = Mock(ColumnConfigToColumnBinder)
    def columnBinder = Mock(ColumnBinder)
    def persistentPropertyToPropertyConfig = Mock(PersistentPropertyToPropertyConfig)
    def typeNameProvider = Mock(TypeNameProvider)

    def binder = new SimpleValueBinder(namingStrategy,
            columnConfigToColumnBinder,
            columnBinder,
            persistentPropertyToPropertyConfig,
            typeNameProvider)

    def "sets type from provider when present and applies type params"() {
        given:
        def prop = Mock(PersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def sv = Mock(SimpleValue)
        sv.getTable() >> null
        def props = new Properties(); props.setProperty('p1','v1')

        // stubs
        persistentPropertyToPropertyConfig.toPropertyConfig(prop) >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        typeNameProvider.getTypeName(prop, mapping) >> "custom.Type"
        pc.getTypeParams() >> props
        pc.isDerived() >> false
        pc.getColumns() >> null
        prop.getType() >> String
        prop.isNullable() >> true

        when:
        binder.bindSimpleValue(prop, null, sv, "p")

        then:
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
        def prop = Mock(PersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def sv = Mock(SimpleValue)
        sv.getTable() >> null

        persistentPropertyToPropertyConfig.toPropertyConfig(prop) >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        typeNameProvider.getTypeName(prop, mapping) >> null
        pc.isDerived() >> false
        pc.getColumns() >> null
        prop.getType() >> Integer
        prop.isNullable() >> true

        when:
        binder.bindSimpleValue(prop, null, sv, null)

        then:
        1 * sv.setTypeName(Integer.name)
        1 * columnBinder.bindColumn(prop, null, _, null, null, null) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
    }

    def "derived property adds no columns but adds formula, except TenantId"() {
        given:
        def prop = Mock(PersistentProperty)
        def tenantProp = Mock(TenantId)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def tenantPc = Mock(PropertyConfig)
        def sv = Mock(SimpleValue)
        def sv2 = Mock(SimpleValue)

        persistentPropertyToPropertyConfig.toPropertyConfig(prop) >> pc
        persistentPropertyToPropertyConfig.toPropertyConfig(tenantProp) >> tenantPc
        prop.getOwner() >> owner
        tenantProp.getOwner() >> owner
        owner.getMappedForm() >> mapping
        typeNameProvider.getTypeName(_, _) >> 'X'

        pc.isDerived() >> true
        pc.getFormula() >> 'x+y'
        tenantPc.isDerived() >> true
        tenantPc.getFormula() >> 'ignored'

        when:
        binder.bindSimpleValue(prop, null, sv, null)

        then:
        1 * sv.addFormula({ it.getFormula() == 'x+y' })
        0 * columnBinder.bindColumn(_, _, _, _, _, _)

        when:
        binder.bindSimpleValue(tenantProp, null, sv2, null)

        then:
        0 * sv2.addFormula(_)
        1 * columnBinder.bindColumn(_, _, _, _, _, _) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
    }

    def "applies generator and maps sequence param to SequenceStyleGenerator.SEQUENCE_PARAM"() {
        given:
        def prop = Mock(PersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def sv = Mock(org.hibernate.mapping.BasicValue)
        sv.getTable() >> null
        def genProps = new Properties(); genProps.setProperty('sequence','seq_name'); genProps.setProperty('foo','bar')

        persistentPropertyToPropertyConfig.toPropertyConfig(prop) >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        typeNameProvider.getTypeName(prop, mapping) >> 'Y'
        pc.isDerived() >> false
        pc.getColumns() >> null
        pc.getGenerator() >> 'sequence'
        pc.getTypeParams() >> genProps
        prop.getType() >> String

        when:
        binder.bindSimpleValue(prop, null, sv, null)

        then:
        1 * columnBinder.bindColumn(prop, null, _, null, null, null) >> { args ->
            args[2].setName("testColumn")
        }
        1 * sv.setCustomIdGeneratorCreator(_)
    }

    def "binds for each provided column config and adds to table and simple value"() {
        given:
        def prop = Mock(PersistentProperty)
        def parent = Mock(PersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def cc1 = new ColumnConfig(name: 'c1')
        def cc2 = new ColumnConfig(name: 'c2')
        def sv = Mock(SimpleValue)
        sv.getTable() >> null

        persistentPropertyToPropertyConfig.toPropertyConfig(prop) >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        typeNameProvider.getTypeName(prop, mapping) >> 'Z'
        pc.isDerived() >> false
        pc.getColumns() >> [cc1, cc2]
        prop.isNullable() >> true
        parent.isNullable() >> false
        prop.getType() >> String

        when:
        binder.bindSimpleValue(prop, parent, sv, 'path')

        then:
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
