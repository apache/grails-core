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
package org.grails.orm.hibernate.cfg.domainbinding.util

import groovy.transform.CompileStatic

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.hibernate.HibernateException
import org.hibernate.MappingException
import org.hibernate.boot.spi.BootstrapContext
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.boot.spi.MetadataBuildingOptions
import org.hibernate.metamodel.spi.ValueAccess
import org.hibernate.resource.beans.spi.ManagedBean
import org.hibernate.resource.beans.spi.ManagedBeanRegistry
import org.hibernate.usertype.CompositeUserType
import org.springframework.beans.BeanInstantiationException
import spock.lang.Specification

import java.lang.reflect.Type

class CompositeUserTypeColumnsSpec extends Specification {

    PersistentEntityNamingStrategy namingStrategy = Mock(PersistentEntityNamingStrategy) {
        resolveColumnName(_ as String) >> { String name -> name.replaceAll(/([A-Z])/) { '_' + it[0].toLowerCase() } }
    }
    CompositeUserTypeColumns subject = new CompositeUserTypeColumns(namingStrategy, new BackticksRemover())

    void "the attributes of a record are its components in alphabetical order"() {
        expect:
        CompositeUserTypeColumns.attributesOf(UtilMoneyRecord)*.name == ['amount', 'currency']
        CompositeUserTypeColumns.attributesOf(UtilMoneyRecord)*.type == [long, String] as List<Type>
    }

    void "the attributes of a class are its fields and readable properties, less the static, transient and excluded ones"() {
        expect: 'a getter-only property counts, a transient field, a static field, a transients entry and the Groovy properties do not'
        CompositeUserTypeColumns.attributesOf(UtilMoneyBean)*.name == ['amount', 'currency', 'formatted']

        and: 'an attribute is typed by its field, or by its getter when it has no field'
        CompositeUserTypeColumns.attributesOf(UtilMoneyBean)*.type == [long, String, String] as List<Type>
    }

    void "the attributes of a class without accessors are its private fields"() {
        expect:
        CompositeUserTypeColumns.attributesOf(UtilMoneyFields)*.name == ['amount', 'currency']
    }

    void "the class of an attribute is its type, or the raw class of a parameterized type"() {
        expect:
        new CompositeUserTypeColumns.Attribute('amount', long).rawType() == long
        new CompositeUserTypeColumns.Attribute('currency', String).rawType() == String
        new CompositeUserTypeColumns.Attribute('tags', UtilAttributeTypes.getDeclaredField('tags').genericType).rawType() == List
    }

    void "a composite user type is instantiated through its default constructor without a building context"() {
        expect:
        subject.userTypeOf(property('fee', [])) instanceof UtilMoneyType
    }

    void "a composite user type is a managed bean of the bean registry when the building options allow extensions in CDI"() {
        given: 'a building context that allows extensions in CDI, whose bean registry holds a user type without a default constructor'
        UtilCdiMoneyType managed = new UtilCdiMoneyType('cdi')
        ManagedBeanRegistry registry = Mock(ManagedBeanRegistry)
        CompositeUserTypeColumns cdiSubject = new CompositeUserTypeColumns(
                namingStrategy, new BackticksRemover(), buildingContext(true, registry))
        HibernatePersistentProperty property = property('fee', [], UtilCdiMoneyType)

        when:
        CompositeUserType<?> userType = cdiSubject.userTypeOf(property)

        then: 'the registry supplies the instance'
        1 * registry.getBean(UtilCdiMoneyType) >> Stub(ManagedBean) { getBeanInstance() >> managed }
        userType.is(managed)

        when: 'the columns of the property are named'
        List<String> names = cdiSubject.columnNamesOf(property, '')

        then: 'the embeddable class of that instance names them'
        1 * registry.getBean(UtilCdiMoneyType) >> Stub(ManagedBean) { getBeanInstance() >> managed }
        names == ['fee_amount', 'fee_currency']
    }

    void "a composite user type is instantiated through its default constructor when the building options do not allow extensions in CDI"() {
        given:
        ManagedBeanRegistry registry = Mock(ManagedBeanRegistry)
        CompositeUserTypeColumns plainSubject = new CompositeUserTypeColumns(
                namingStrategy, new BackticksRemover(), buildingContext(false, registry))

        when:
        CompositeUserType<?> userType = plainSubject.userTypeOf(property('fee', []))

        then:
        userType instanceof UtilMoneyType
        0 * registry._
    }

    void "a composite user type without a default constructor is an instantiation error without a bean container"() {
        when:
        subject.userTypeOf(property('fee', [], UtilCdiMoneyType))

        then:
        BeanInstantiationException e = thrown()
        e.message.contains(UtilCdiMoneyType.name)
    }

    void "a configured column keeps its configuration and an attribute without one is named after the property and the attribute"() {
        given: 'a property that names its first column, configures its second without a name, and maps nothing for the third'
        ColumnConfig named = new ColumnConfig(name: '`Amount`', sqlType: 'bigint')
        ColumnConfig unnamed = new ColumnConfig(sqlType: 'char(3)', length: 3, unique: true, index: 'idx', comment: 'c',
                defaultValue: 'EUR', read: 'r', write: 'w')
        HibernatePersistentProperty property = property('unitPrice', [named, unnamed])
        List<CompositeUserTypeColumns.Attribute> attributes = [attribute('amount'), attribute('currency'), attribute('scale')]

        when:
        List<ColumnConfig> columns = subject.columnConfigsOf(property, '', UtilMoneyRecord, attributes)

        then: 'the named configuration is used as it is'
        columns[0].is(named)

        and: 'the unnamed one is copied with its settings and the default name'
        !columns[1].is(unnamed)
        columns[1].name == 'unit_price_currency'
        columns[1].sqlType == 'char(3)'
        columns[1].length == 3
        columns[1].unique
        columns[1].index == 'idx'
        columns[1].comment == 'c'
        columns[1].defaultValue == 'EUR'
        columns[1].read == 'r'
        columns[1].write == 'w'

        and: 'the missing one is a new configuration with the default name'
        columns[2].name == 'unit_price_scale'
        columns[2].sqlType == null
    }

    void "the default column name of an attribute of an embedded property is prefixed with the path"() {
        given:
        HibernatePersistentProperty property = property('amount', [])

        expect:
        subject.columnConfigsOf(property, 'orderLine', UtilMoneyRecord, [attribute('currency')])*.name == ['order_line_amount_currency']
    }

    void "more columns than attributes is a mapping error that names the property, the counts and the attributes"() {
        given:
        HibernatePersistentProperty property = property('amount',
                [new ColumnConfig(name: 'first'), new ColumnConfig(name: 'second'), new ColumnConfig(name: 'third')])

        when:
        subject.columnConfigsOf(property, '', UtilMoneyRecord, [attribute('amount'), attribute('currency')])

        then:
        MappingException e = thrown()
        e.message.contains('amount')
        e.message.contains('util.UtilPayment')
        e.message.contains('3 columns')
        e.message.contains('2 attributes')
        e.message.contains(UtilMoneyRecord.name)
        e.message.contains(UtilMoneyType.name)
        e.message.contains('[amount, currency]')
    }

    void "the column names of a property come from its composite user type: the configured names, or the default ones"() {
        given: 'a property mapped with a composite user type whose embeddable class has two attributes'
        HibernatePersistentProperty property = property('fee', columns.collect { new ColumnConfig(name: it) })

        expect:
        subject.columnNamesOf(property, path) == expected

        where:
        columns                        | path   || expected
        []                             | ''     || ['fee_amount', 'fee_currency']
        ['fee_value']                  | ''     || ['fee_value', 'fee_currency']
        ['`fee_value`', 'fee_currency'] | ''     || ['`fee_value`', 'fee_currency']
        []                             | 'cost' || ['cost_fee_amount', 'cost_fee_currency']
    }

    void "the user type and the column names of a property that is not mapped with a composite user type are a mapping error"() {
        given:
        HibernatePersistentProperty property = Mock(HibernatePersistentProperty) {
            getName() >> 'reference'
            getOwner() >> owner()
            getCompositeUserType() >> null
        }

        when:
        subject.columnNamesOf(property, '')

        then:
        MappingException e = thrown()
        e.message.contains('reference')
        e.message.contains('util.UtilPayment')

        when:
        subject.userTypeOf(property)

        then:
        MappingException userTypeError = thrown()
        userTypeError.message == e.message
    }

    private HibernatePersistentProperty property(
            String name, List<ColumnConfig> columns, Class<? extends CompositeUserType<?>> userType = UtilMoneyType) {
        PropertyConfig config = new PropertyConfig()
        config.columns = columns
        Mock(HibernatePersistentProperty) {
            getName() >> name
            getOwner() >> owner()
            getHibernateMappedForm() >> config
            getCompositeUserType() >> userType
        }
    }

    private MetadataBuildingContext buildingContext(boolean allowExtensionsInCdi, ManagedBeanRegistry registry) {
        MetadataBuildingOptions options = Stub(MetadataBuildingOptions) {
            isAllowExtensionsInCdi() >> allowExtensionsInCdi
        }
        BootstrapContext bootstrapContext = Stub(BootstrapContext) {
            getManagedBeanRegistry() >> registry
        }
        Stub(MetadataBuildingContext) {
            getBuildingOptions() >> options
            getBootstrapContext() >> bootstrapContext
        }
    }

    private PersistentEntity owner() {
        Stub(PersistentEntity) {
            getName() >> 'org.grails.orm.hibernate.cfg.domainbinding.util.UtilPayment'
        }
    }

    private static CompositeUserTypeColumns.Attribute attribute(String name) {
        new CompositeUserTypeColumns.Attribute(name, String)
    }
}

/** An embeddable class described by a record: the components are the attributes. */
record UtilMoneyRecord(String currency, long amount) implements Serializable {}

/**
 * An embeddable bean: a getter-only property counts as an attribute, while a transient field, a static field, a
 * property listed in {@code transients} and the properties every Groovy object has do not.
 */
@CompileStatic
class UtilMoneyBean implements Serializable {
    long amount
    String currency
    transient String note
    static String label = 'money'
    static transients = ['display']

    String getDisplay() {
        "${amount} ${currency}"
    }

    String getFormatted() {
        amount.toString()
    }
}

/** An embeddable class with private fields and no accessors, as in the {@link CompositeUserType} javadoc. */
@CompileStatic
class UtilMoneyFields {
    private long amount
    private String currency
}

/** A holder of attribute types: a parameterized one resolves to its raw class. */
@CompileStatic
class UtilAttributeTypes {
    List<String> tags
}

/** The same user type without a default constructor, which only a bean container can create. */
@CompileStatic
class UtilCdiMoneyType extends UtilMoneyType {

    final String label

    UtilCdiMoneyType(String label) {
        this.label = label
    }
}

/** A composite user type with a default constructor, whose embeddable class is {@link UtilMoneyRecord}. */
@CompileStatic
class UtilMoneyType implements CompositeUserType<UtilMoneyRecord> {

    @Override
    Object getPropertyValue(UtilMoneyRecord component, int property) {
        switch (property) {
            case 0:
                return component.amount()
            case 1:
                return component.currency()
            default:
                throw new HibernateException("Unknown property index ${property}")
        }
    }

    @Override
    UtilMoneyRecord instantiate(ValueAccess values) {
        new UtilMoneyRecord(values.getValue(1, String), values.getValue(0, Long))
    }

    @Override
    Class<?> embeddable() {
        UtilMoneyRecord
    }

    @Override
    Class<UtilMoneyRecord> returnedClass() {
        UtilMoneyRecord
    }

    @Override
    boolean equals(UtilMoneyRecord x, UtilMoneyRecord y) {
        x == y
    }

    @Override
    int hashCode(UtilMoneyRecord x) {
        x == null ? 0 : x.hashCode()
    }

    @Override
    UtilMoneyRecord deepCopy(UtilMoneyRecord value) {
        value
    }

    @Override
    boolean isMutable() {
        false
    }

    @Override
    Serializable disassemble(UtilMoneyRecord value) {
        value
    }

    @Override
    UtilMoneyRecord assemble(Serializable cached, Object owner) {
        (UtilMoneyRecord) cached
    }

    @Override
    UtilMoneyRecord replace(UtilMoneyRecord detached, UtilMoneyRecord managed, Object owner) {
        detached
    }
}
