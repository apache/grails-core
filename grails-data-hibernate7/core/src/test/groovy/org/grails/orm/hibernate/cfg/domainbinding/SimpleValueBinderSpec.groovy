/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
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

    abstract static class TestTenantId extends TenantId<PropertyConfig> implements HibernatePersistentProperty {
        TestTenantId(PersistentEntity owner, MappingContext context, String name, Class type) {
            super(owner, context, name, type)
        }
    }

    def namingStrategy = Mock(PersistentEntityNamingStrategy)
    def columnConfigToColumnBinder = Mock(ColumnConfigToColumnBinder)
    def columnBinder = Mock(ColumnBinder)
    def jdbcEnvironment = Mock(org.hibernate.engine.jdbc.env.spi.JdbcEnvironment)
    def grailsSequenceWrapper = Mock(org.grails.orm.hibernate.cfg.domainbinding.generator.GrailsSequenceWrapper)
    def metadataBuildingContext = Mock(org.hibernate.boot.spi.MetadataBuildingContext)

    def binder = new SimpleValueBinder(metadataBuildingContext,
            namingStrategy,
            columnConfigToColumnBinder,
            columnBinder,
            jdbcEnvironment,
            grailsSequenceWrapper)

    def "sets type from provider when present and applies type params"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def sv = Mock(SimpleValue)
        sv.getTable() >> null
        def props = new Properties(); props.setProperty('p1','v1')

        // stubs
        prop.getMappedForm() >> pc
        prop.getHibernateMappedForm() >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        owner.getHibernateMappedForm() >> mapping
        _ * prop.getHibernateMappedForm() >> pc
        _ * owner.getHibernateMappedForm() >> mapping
        prop.getTypeName() >> "custom.Type"
        pc.getTypeParams() >> props
        pc.isDerived() >> false
        pc.getColumns() >> null
        prop.getType() >> String
        prop.isNullable() >> true

        when:
        binder.bindSimpleValue(prop, null, sv, "p")

        then:
        _ * prop.getTypeName(sv) >> "custom.Type"
        _ * prop.getTypeParameters(sv) >> props
        _ * sv.setTypeName("custom.Type")
        _ * sv.setTypeParameters({ it.getProperty('p1') == 'v1' })
        _ * columnBinder.bindColumn(prop, null, _, null, 'p', null) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
        _ * columnConfigToColumnBinder.bindColumnConfigToColumn(_, null, pc) >> { args ->
            def column = args[0] as Column
            column.setName("testColumn")
        }
    }

    def "falls back to property type when provider returns null"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def sv = Mock(SimpleValue)
        sv.getTable() >> null

        prop.getMappedForm() >> pc
        prop.getHibernateMappedForm() >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        owner.getHibernateMappedForm() >> mapping
        _ * prop.getHibernateMappedForm() >> pc
        _ * owner.getHibernateMappedForm() >> mapping
        prop.getTypeName() >> null
        pc.isDerived() >> false
        pc.getColumns() >> null
        prop.getType() >> Integer
        prop.isNullable() >> true

        when:
        binder.bindSimpleValue(prop, null, sv, null)

        then:
        _ * prop.getTypeName(sv) >> Integer.name
        _ * prop.getTypeParameters(sv) >> null
        _ * sv.setTypeName(Integer.name)
        _ * columnBinder.bindColumn(prop, null, _, null, null, null) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
    }

    def "derived property adds no columns but adds formula, except TenantId"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        def tenantProp = Mock(TestTenantId)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def tenantPc = Mock(PropertyConfig)
        def sv = Mock(SimpleValue)
        def sv2 = Mock(SimpleValue)

        prop.getMappedForm() >> pc
        prop.getHibernateMappedForm() >> pc
        tenantProp.getMappedForm() >> tenantPc
        tenantProp.getHibernateMappedForm() >> tenantPc
        prop.getOwner() >> owner
        tenantProp.getOwner() >> owner
        owner.getMappedForm() >> mapping
        owner.getHibernateMappedForm() >> mapping
        _ * prop.getHibernateMappedForm() >> pc
        _ * tenantProp.getHibernateMappedForm() >> tenantPc
        _ * owner.getHibernateMappedForm() >> mapping
        prop.getTypeName() >> 'X'

        pc.isDerived() >> true
        pc.getFormula() >> 'x+y'
        tenantPc.isDerived() >> true
        tenantPc.getFormula() >> 'ignored'

        when:
        binder.bindSimpleValue(prop, null, sv, null)

        then:
        _ * prop.getTypeName(sv) >> 'X'
        _ * prop.getTypeParameters(sv) >> null
        _ * sv.addFormula({ it.getFormula() == 'x+y' })
        0 * columnBinder.bindColumn(_, _, _, _, _, _)

        when:
        binder.bindSimpleValue(tenantProp, null, sv2, null)

        then:
        _ * tenantProp.getTypeName(sv2) >> 'X'
        _ * tenantProp.getTypeParameters(sv2) >> null
        0 * sv2.addFormula(_)
        _ * columnBinder.bindColumn(_, _, _, _, _, _) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
    }

    def "applies generator and maps sequence param to SequenceStyleGenerator.SEQUENCE_PARAM"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def sv = Mock(org.hibernate.mapping.BasicValue)
        sv.getTable() >> null
        def genProps = new Properties(); genProps.setProperty('sequence','seq_name'); genProps.setProperty('foo','bar')

        prop.getMappedForm() >> pc
        prop.getHibernateMappedForm() >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        owner.getHibernateMappedForm() >> mapping
        _ * prop.getHibernateMappedForm() >> pc
        _ * owner.getHibernateMappedForm() >> mapping
        prop.getTypeName() >> 'Y'
        pc.isDerived() >> false
        pc.getColumns() >> null
        pc.getGenerator() >> 'sequence'
        pc.getTypeParams() >> genProps
        prop.getType() >> String

        when:
        binder.bindSimpleValue(prop, null, sv, null)

        then:
        _ * prop.getTypeName(sv) >> 'Y'
        _ * prop.getTypeParameters(sv) >> null
        _ * columnBinder.bindColumn(prop, null, _, null, null, null) >> { args ->
            args[2].setName("testColumn")
        }
        _ * sv.setCustomIdGeneratorCreator(_)
    }

    def "binds for each provided column config and adds to table and simple value"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        def parent = Mock(HibernatePersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def cc1 = new ColumnConfig(name: 'c1')
        def cc2 = new ColumnConfig(name: 'c2')
        def sv = Mock(SimpleValue)
        sv.getTable() >> null

        prop.getMappedForm() >> pc
        prop.getHibernateMappedForm() >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        owner.getHibernateMappedForm() >> mapping
        _ * prop.getHibernateMappedForm() >> pc
        _ * owner.getHibernateMappedForm() >> mapping
        prop.getTypeName() >> 'Z'
        pc.isDerived() >> false
        pc.getColumns() >> [cc1, cc2]
        prop.isNullable() >> true
        parent.isNullable() >> false
        prop.getType() >> String

        when:
        binder.bindSimpleValue(prop, parent, sv, 'path')

        then:
        _ * prop.getTypeName(sv) >> 'Z'
        _ * prop.getTypeParameters(sv) >> null
        _ * columnConfigToColumnBinder.bindColumnConfigToColumn(_, cc1, pc) >> { args ->
            def column = args[0] as Column
            column.setName("testColumn")
        }
        _ * columnConfigToColumnBinder.bindColumnConfigToColumn(_, cc2, pc) >> { args ->
            def column = args[0] as Column
            column.setName("testColumn")
        }
        _ * columnBinder.bindColumn(prop, parent, _, cc1, 'path', null) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
        _ * columnBinder.bindColumn(prop, parent, _, cc2, 'path', null) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
        _ * sv.addColumn(_ as Column)
    }

    def "bindSimpleValue creates and returns BasicValue"() {
        given:
        def prop = Mock(HibernatePersistentProperty)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mapping = Mock(Mapping)
        def pc = Mock(PropertyConfig)
        def table = new org.hibernate.mapping.Table("test_table")
        def mappings = Mock(org.hibernate.boot.spi.InFlightMetadataCollector)
        metadataBuildingContext.getMetadataCollector() >> mappings

        prop.getMappedForm() >> pc
        prop.getHibernateMappedForm() >> pc
        prop.getOwner() >> owner
        owner.getMappedForm() >> mapping
        owner.getHibernateMappedForm() >> mapping
        _ * prop.getHibernateMappedForm() >> pc
        _ * owner.getHibernateMappedForm() >> mapping
        prop.getTypeName(_ as SimpleValue) >> String.name
        pc.isDerived() >> false
        pc.getColumns() >> null
        prop.getType() >> String
        prop.isNullable() >> true

        when:
        def result = binder.bindSimpleValue(prop, null, table, "path")

        then:
        _ * columnBinder.bindColumn(prop, null, _, null, "path", table) >> { args ->
            def column = args[2] as Column
            column.setName("testColumn")
        }
        result instanceof org.hibernate.mapping.BasicValue
        result.getTable() == table
        result.getTypeName() == String.name
    }
}
