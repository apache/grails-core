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

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.hibernate.boot.spi.BootstrapContext
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.boot.spi.MetadataBuildingOptions
import org.hibernate.resource.beans.spi.ManagedBean
import org.hibernate.resource.beans.spi.ManagedBeanRegistry
import org.hibernate.usertype.CompositeUserType
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.CompositeUserTypeColumns
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.UtilCdiMoneyType

class ColumnNameForPropertyAndPathFetcherSpec extends Specification {

    def backticksRemover = new BackticksRemover()

    def "when grailsProp returns a column name then it is used"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                defaultColumnFetcher,
                backticksRemover
        )

        def grailsProp = Mock(HibernatePersistentProperty)
        def cc = Mock(ColumnConfig)

        grailsProp.getColumnName(cc) >> "explicit_col"

        when:
        def result = fetcher.getColumnNameForPropertyAndPath(grailsProp, "somePath", cc)

        then:
        result == "explicit_col"
    }

    @Unroll
    def "when grailsProp returns null then builds from path '#path' and default column '#defaultCol' with backticks removed"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                defaultColumnFetcher,
                backticksRemover
        )

        def grailsProp = Mock(HibernatePersistentProperty)

        grailsProp.getColumnName(null) >> null
        namingStrategy.resolveColumnName(path) >> resolvedPath
        defaultColumnFetcher.getDefaultColumnName(grailsProp) >> defaultCol

        when:
        def result = fetcher.getColumnNameForPropertyAndPath(grailsProp, path, null)

        then:
        result == expected

        where:
        path              | resolvedPath       | defaultCol           || expected
        "`order`"        | "`order`"          | "`customer_id`"      || "order_customer_id"
        "invoice"        | "invoice"          | "line_item_id"       || "invoice_line_item_id"
    }

    def "when grailsProp returns null and path is empty falls back to default column name only"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                defaultColumnFetcher,
                backticksRemover
        )

        def grailsProp = Mock(HibernatePersistentProperty)

        grailsProp.getColumnName(null) >> null
        defaultColumnFetcher.getDefaultColumnName(grailsProp) >> "only_default"

        when:
        def result = fetcher.getColumnNameForPropertyAndPath(grailsProp, null, null)

        then:
        result == "only_default"
    }

    def "the column names of a property with one column are that column name, resolved without a column configuration"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def compositeUserTypeColumns = Mock(CompositeUserTypeColumns)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                defaultColumnFetcher,
                backticksRemover,
                compositeUserTypeColumns
        )
        def grailsProp = Mock(HibernatePersistentProperty) {
            getCompositeUserType() >> null
            getHibernateMappedForm() >> new PropertyConfig()
            getColumnName(null) >> "`explicit_col`"
        }

        when:
        def result = fetcher.getColumnNamesForPropertyAndPath(grailsProp, "somePath")

        then:
        result == ["`explicit_col`"]
        0 * compositeUserTypeColumns._
    }

    def "the column names of a property mapped with a composite user type are the names of its attribute columns"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def compositeUserTypeColumns = Mock(CompositeUserTypeColumns)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                defaultColumnFetcher,
                backticksRemover,
                compositeUserTypeColumns
        )
        def grailsProp = Mock(HibernatePersistentProperty) {
            getCompositeUserType() >> CompositeUserType
        }
        compositeUserTypeColumns.columnNamesOf(grailsProp, "somePath") >> ["amount_amount", "amount_currency"]

        when:
        def result = fetcher.getColumnNamesForPropertyAndPath(grailsProp, "somePath")

        then: "the property is not treated as a single column"
        result == ["amount_amount", "amount_currency"]
        0 * grailsProp.getColumnName(_)
        0 * defaultColumnFetcher._
    }

    def "expands every configured column in config order"() {
        given:
        def namingStrategy = Mock(PersistentEntityNamingStrategy)
        def defaultColumnFetcher = Mock(DefaultColumnNameFetcher)
        def fetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnFetcher, backticksRemover)
        def firstColumn = new ColumnConfig(name: 'parent_code')
        def secondColumn = new ColumnConfig(name: 'parent_revision')
        def property = Mock(HibernatePersistentProperty) {
            getHibernateMappedForm() >> new PropertyConfig(columns: [firstColumn, secondColumn])
            getColumnName(firstColumn) >> 'parent_code'
            getColumnName(secondColumn) >> 'parent_revision'
        }

        when:
        def names = fetcher.getColumnNamesForPropertyAndPath(property, 'child_path')

        then:
        names == ['parent_code', 'parent_revision']
        0 * property.getColumnName(null)
        0 * defaultColumnFetcher._
    }

    def "keeps configured column order when an explicit column is followed by an implicit column"() {
        given:
        def namingStrategy = Stub(PersistentEntityNamingStrategy) {
            resolveColumnName('childPath') >> 'child_path'
        }
        def defaultColumnFetcher = Stub(DefaultColumnNameFetcher) {
            getDefaultColumnName(_) >> 'parent_id'
        }
        def fetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnFetcher, backticksRemover)
        def explicitColumn = new ColumnConfig(name: '`parent_code`')
        def implicitColumn = new ColumnConfig()
        def property = Mock(HibernatePersistentProperty) {
            getHibernateMappedForm() >> new PropertyConfig(columns: [explicitColumn, implicitColumn])
            getColumnName(explicitColumn) >> '`parent_code`'
            getColumnName(implicitColumn) >> null
        }

        when:
        def names = fetcher.getColumnNamesForPropertyAndPath(property, 'childPath')

        then:
        names == ['`parent_code`', 'child_path_parent_id']
        0 * property.getColumnName(null)
    }

    def "a fetcher built with a building context resolves a composite user type as the binder of its property does"() {
        given: "a building context that allows extensions in CDI, whose bean registry creates a user type without a default constructor"
        UtilCdiMoneyType managed = new UtilCdiMoneyType('cdi')
        ManagedBeanRegistry registry = Mock(ManagedBeanRegistry)
        MetadataBuildingOptions options = Stub(MetadataBuildingOptions) {
            isAllowExtensionsInCdi() >> true
        }
        BootstrapContext bootstrapContext = Stub(BootstrapContext) {
            getManagedBeanRegistry() >> registry
        }
        MetadataBuildingContext buildingContext = Stub(MetadataBuildingContext) {
            getBuildingOptions() >> options
            getBootstrapContext() >> bootstrapContext
        }
        def namingStrategy = Stub(PersistentEntityNamingStrategy) {
            resolveColumnName(_ as String) >> { String name -> name }
        }
        def fetcher = new ColumnNameForPropertyAndPathFetcher(
                namingStrategy,
                new DefaultColumnNameFetcher(namingStrategy, backticksRemover),
                backticksRemover,
                buildingContext
        )
        def grailsProp = Mock(HibernatePersistentProperty) {
            getName() >> 'fee'
            getOwner() >> Stub(PersistentEntity)
            getHibernateMappedForm() >> new PropertyConfig()
            getCompositeUserType() >> UtilCdiMoneyType
        }

        when:
        def result = fetcher.getColumnNamesForPropertyAndPath(grailsProp, "")

        then: "the registry supplies the user type whose embeddable class names the columns"
        1 * registry.getBean(UtilCdiMoneyType) >> Stub(ManagedBean) { getBeanInstance() >> managed }
        result == ["fee_amount", "fee_currency"]
    }
}
