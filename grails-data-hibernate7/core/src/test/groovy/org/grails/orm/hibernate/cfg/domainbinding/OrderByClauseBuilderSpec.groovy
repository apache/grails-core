/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.DatastoreConfigurationException
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Component
import org.hibernate.mapping.JoinedSubclass
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SingleTableSubclass
import org.hibernate.mapping.Table
import spock.lang.Subject
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.util.OrderByClauseBuilder

class OrderByClauseBuilderSpec extends HibernateGormDatastoreSpec {

    @Subject
    OrderByClauseBuilder builder = new OrderByClauseBuilder()

    private RootClass entityClass
    private RootClass componentEntityClass

    def setup() {
        def ctx = getGrailsDomainBinder().getMetadataBuildingContext()
        def table = new Table('test', 'order_entity')

        entityClass = new RootClass(ctx)
        entityClass.setEntityName('OrderEntity')
        entityClass.setTable(table)
        entityClass.setIdentifier(basicValue(ctx, table, 'id'))
        entityClass.addProperty(simpleProperty(ctx, table, 'name',  'name'))
        entityClass.addProperty(simpleProperty(ctx, table, 'age',   'age'))
        entityClass.addProperty(simpleProperty(ctx, table, 'other', 'other_column'))

        def compTable = new Table('test', 'comp_entity')
        componentEntityClass = new RootClass(ctx)
        componentEntityClass.setEntityName('CompEntity')
        componentEntityClass.setTable(compTable)
        componentEntityClass.setIdentifier(basicValue(ctx, compTable, 'id'))

        def comp = new Component(ctx, compTable, componentEntityClass)
        comp.addProperty(simpleProperty(ctx, compTable, 'c1', 'comp_c1'))
        comp.addProperty(simpleProperty(ctx, compTable, 'c2', 'comp_c2'))
        def compProp = new Property()
        compProp.setName('comp')
        compProp.setValue(comp)
        componentEntityClass.addProperty(compProp)
    }

    void 'null hqlOrderBy returns null'() {
        expect:
        builder.buildOrderByClause(null, entityClass, 'role', 'asc') == null
    }

    void 'empty hqlOrderBy returns identifier column with asc'() {
        expect:
        builder.buildOrderByClause('', entityClass, 'role', 'asc') == 'id asc'
    }

    @Unroll
    void "single property '#hql' with defaultOrder '#defaultOrder' returns '#expected'"() {
        expect:
        builder.buildOrderByClause(hql, entityClass, 'role', defaultOrder) == expected

        where:
        hql          | defaultOrder | expected
        'name'       | 'asc'        | 'name asc'
        'name'       | 'desc'       | 'name desc'
        'name asc'   | 'desc'       | 'name asc'
        'name desc'  | 'asc'        | 'name desc'
        'name ASC'   | 'desc'       | 'name asc'
        'name DESC'  | 'asc'        | 'name desc'
    }

    void 'custom column name is used in order clause'() {
        expect:
        builder.buildOrderByClause('other', entityClass, 'role', 'asc') == 'other_column asc'
    }

    void 'multiple properties with mixed directions'() {
        expect:
        builder.buildOrderByClause('name, age desc', entityClass, 'role', 'asc') == 'name asc, age desc'
    }

    void 'component property expands to all its columns'() {
        expect:
        builder.buildOrderByClause('comp', componentEntityClass, 'role', 'asc') == 'comp_c1 asc, comp_c2 asc'
    }

    void 'non-existent property throws DatastoreConfigurationException'() {
        when:
        builder.buildOrderByClause('nonExistent', entityClass, 'role', 'asc')

        then:
        def ex = thrown(DatastoreConfigurationException)
        ex.message.contains('OrderEntity.nonExistent')
    }

    void 'double direction token throws DatastoreConfigurationException'() {
        when:
        builder.buildOrderByClause('name asc desc', entityClass, 'role', 'asc')

        then:
        thrown(DatastoreConfigurationException)
    }

    void 'inherited property from parent in joined subclass receives table prefix'() {

        given:
        def ctx  = getGrailsDomainBinder().getMetadataBuildingContext()
        def subTable = new Table('test', 'sub_entity')
        def sub = new JoinedSubclass(entityClass, ctx)
        sub.setEntityName('SubEntity')
        sub.setTable(subTable)
        sub.addProperty(simpleProperty(ctx, subTable, 'extra', 'extra_col'))

        expect: 'property from root table gets no prefix when sorting on root class'
        builder.buildOrderByClause('name', entityClass, 'role', 'asc') == 'name asc'

        and: 'property from root table gets its table prefix when sorting on the subclass'
        builder.buildOrderByClause('name', sub, 'role', 'asc') == 'order_entity.name asc'

        and: 'property from subclass table gets no prefix when sorting on the subclass'
        builder.buildOrderByClause('extra', sub, 'role', 'asc') == 'extra_col asc'
    }

    void 'single-table subclass property is sorted without table prefix'() {

        given:
        def ctx  = getGrailsDomainBinder().getMetadataBuildingContext()
        entityClass.setClassName('org.grails.orm.hibernate.cfg.domainbinding.ParentEntity')
        def sub = new SingleTableSubclass(entityClass, ctx)
        sub.setEntityName('ChildEntity')
        sub.setClassName('org.grails.orm.hibernate.cfg.domainbinding.ChildEntity')
        sub.addProperty(simpleProperty(ctx, entityClass.getTable(), 'childProp', 'child_prop'))

        expect: 'parent property has no prefix on the subclass'
        builder.buildOrderByClause('name', sub, 'role', 'asc') == 'name asc'

        and: 'subclass-own property has no prefix'
        builder.buildOrderByClause('childProp', sub, 'role', 'asc') == 'child_prop asc'
    }

    // ---- helpers --------------------------------------------------------

    private static BasicValue basicValue(ctx, Table table, String columnName) {
        def v = new BasicValue(ctx, table)
        v.addColumn(new Column(columnName))
        v
    }

    private static Property simpleProperty(ctx, Table table, String name, String columnName) {
        def prop = new Property()
        prop.setName(name)
        prop.setValue(basicValue(ctx, table, columnName))
        prop
    }
}

// Minimal classes needed for mapped-class assignments in the STI test
class ParentEntity {}
class ChildEntity extends ParentEntity {}
