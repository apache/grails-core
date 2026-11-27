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
package grails.gorm.tests.generated

import java.util.concurrent.atomic.AtomicInteger

import org.hibernate.mapping.Table
import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Unroll

import org.grails.orm.hibernate.CompositeMoney
import org.grails.orm.hibernate.CompositeMoneyType
import org.grails.orm.hibernate.CompositeUserTypeDefaultColumns
import org.grails.orm.hibernate.CompositeUserTypeFieldsTemplated
import org.grails.orm.hibernate.CompositeUserTypeImmutable
import org.grails.orm.hibernate.CompositeUserTypePayment
import org.grails.orm.hibernate.CompositeUserTypeRegistered
import org.grails.orm.hibernate.CompositeUserTypeTemplated
import org.grails.orm.hibernate.CompositeUserTypeTooManyColumns
import org.grails.orm.hibernate.HibernateDatastore

/**
 * A property mapped with a Hibernate {@code CompositeUserType}. The domain binder binds it as a component of the user type, one
 * column per attribute of the user type's embeddable class ({@code CompositeUserTypeBinder}); the generated mode states it as an
 * {@code @Embedded} field with {@code @CompositeType} and the same columns. The fixtures are those of
 * {@code CompositeUserTypeMappingSpec}, which covers the domain binder.
 */
class GeneratedDomainClassesCompositeUserTypeSpec extends Specification {

    static final AtomicInteger BOOTS = new AtomicInteger()

    @AutoCleanup
    HibernateDatastore datastore

    private HibernateDatastore boot(List<Class> group, boolean generated, Map<String, Object> extra = [:]) {
        datastore?.close()
        datastore = new HibernateDatastore([
                'dataSource.url'                  : "jdbc:h2:mem:gcut${BOOTS.incrementAndGet()};LOCK_TIMEOUT=10000".toString(),
                'dataSource.dbCreate'             : 'create-drop',
                'hibernate.generatedDomainClasses': generated,
        ] + extra, group as Class[])
        return datastore
    }

    /** The tables of the boot model as plain data: columns with SQL type, nullability and uniqueness, and the primary key. */
    private static Map<String, Map> schema(HibernateDatastore booted) {
        Map<String, Map> result = new TreeMap<String, Map>()
        for (Table table : booted.metadata.collectTableMappings()) {
            if (table.physicalTable) {
                result[table.name] = [
                        columns   : table.columns.collectEntries { org.hibernate.mapping.Column column ->
                            boolean nullable = column.nullable && !(table.primaryKey != null && table.primaryKey.columns*.name.contains(column.name))
                            [(column.name): "${column.getSqlType(booted.metadata)}${nullable ? '' : ' not null'}${column.unique ? ' unique' : ''}".toString()]
                        },
                        primaryKey: table.primaryKey?.columns*.name?.sort(),
                ]
            }
        }
        return result
    }

    private static Map<String, Object> registeredMoneyType() {
        ['grails.gorm.default.mapping': { 'user-type'(type: CompositeMoneyType, 'class': CompositeMoney) }] as Map<String, Object>
    }

    @Unroll
    void "#label: the generated mode creates the columns of the domain binder"() {
        when:
        Map<String, Map> binder = schema(boot(group, false, extra))
        Map<String, Map> generated = schema(boot(group, true, extra))

        then:
        generated == binder
        generated[table].columns.keySet() == columns as Set

        where:
        label                                              | group                             | extra                 | table                                | columns
        'two properties with their columns and sql types' | [CompositeUserTypePayment]        | [:]                   | 'composite_user_type_payment'        | ['id', 'version', 'reference', 'amount', 'currency', 'fee_amount', 'fee_currency']
        'default column names'                            | [CompositeUserTypeDefaultColumns] | [:]                   | 'composite_user_type_default_columns' | ['id', 'version', 'amount_amount', 'amount_currency']
        'a type registered for the value class'           | [CompositeUserTypeRegistered]     | registeredMoneyType() | 'composite_user_type_registered'     | ['id', 'version', 'money_value', 'money_currency']
        'an embeddable record distinct from the value'    | [CompositeUserTypeTemplated]      | [:]                   | 'composite_user_type_templated'      | ['id', 'version', 'amount_amount', 'amount_currency']
        'an embeddable class with fields and no accessors'  | [CompositeUserTypeFieldsTemplated] | [:]                 | 'composite_user_type_fields_templated' | ['id', 'version', 'amount_amount', 'amount_currency']
        'an immutable value with a transient derived property' | [CompositeUserTypeImmutable]  | [:]                   | 'composite_user_type_immutable'      | ['id', 'version', 'price_amount', 'price_currency']
    }

    void "the generated mode writes a composite value to its columns and reads it back"() {
        given:
        boot([CompositeUserTypePayment], true)

        when:
        Long id = CompositeUserTypePayment.withTransaction {
            new CompositeUserTypePayment(
                    reference: 'INV-1',
                    amount: new CompositeMoney(amount: 1250, currency: 'EUR'),
                    fee: new CompositeMoney(amount: 25, currency: 'EUR')).save(failOnError: true, flush: true).id
        }
        CompositeUserTypePayment reloaded = CompositeUserTypePayment.withNewSession { CompositeUserTypePayment.get(id) }
        List<CompositeUserTypePayment> byAttribute = CompositeUserTypePayment.withNewSession {
            CompositeUserTypePayment.executeQuery('from CompositeUserTypePayment p where p.amount.currency = :currency', [currency: 'EUR'])
        }

        then:
        reloaded.amount == new CompositeMoney(amount: 1250, currency: 'EUR')
        reloaded.fee == new CompositeMoney(amount: 25, currency: 'EUR')
        byAttribute*.id == [id]
    }

    void "more columns than the embeddable class has attributes is refused in the generated mode as in the binder"() {
        when:
        boot([CompositeUserTypeTooManyColumns], true)

        then:
        Throwable thrown = thrown()
        List<String> messages = []
        for (Throwable t = thrown; t != null && !messages.contains(String.valueOf(t.message)); t = t.cause) {
            messages << String.valueOf(t.message)
        }
        messages.any { it.contains('maps 3 columns') && it.contains('has 2 attributes') }
    }
}
