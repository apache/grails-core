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
package org.grails.orm.hibernate

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.Immutable
import groovy.transform.ToString

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.HibernateEntity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.cfg.Settings
import org.hibernate.HibernateException
import org.hibernate.MappingException
import org.hibernate.dialect.H2Dialect
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Column
import org.hibernate.mapping.Component
import org.hibernate.mapping.Table
import org.hibernate.mapping.Value
import org.hibernate.metamodel.spi.ValueAccess
import org.hibernate.resource.beans.container.spi.BeanContainer
import org.hibernate.resource.beans.container.spi.ContainedBean
import org.hibernate.resource.beans.container.spi.FallbackContainedBean
import org.hibernate.resource.beans.spi.BeanInstanceProducer
import org.hibernate.usertype.CompositeUserType
import org.springframework.beans.BeanInstantiationException
import org.springframework.dao.DataIntegrityViolationException
import spock.lang.Specification

/**
 * A property mapped with a Hibernate {@link CompositeUserType} (issue #16688): the type spans several
 * columns, one per attribute of the embeddable class the user type names, in alphabetical attribute order.
 */
class CompositeUserTypeMappingSpec extends Specification {

    void "a composite user type binds one column per embeddable attribute in alphabetical order"() {
        given: 'a datastore with an entity that maps two properties with the composite user type'
        HibernateDatastore datastore = createDatastore('compositeUserTypeExplicit', {}, CompositeUserTypePayment)

        expect: 'each property is a component whose type is the composite user type'
        Component amount = componentOf(datastore, CompositeUserTypePayment, 'amount')
        amount.typeName == CompositeMoneyType.name
        amount.componentClassName == CompositeMoney.name

        and: 'the configured columns are assigned to the attributes in alphabetical attribute order'
        amount.properties*.name == ['amount', 'currency']
        columnNamesOf(amount) == ['amount', 'currency']
        columnNamesOf(componentOf(datastore, CompositeUserTypePayment, 'fee')) == ['fee_amount', 'fee_currency']

        and: 'the column configuration and the nullability of the property apply to the columns'
        Column currency = columnsOf(amount).last()
        currency.sqlType == 'char(3)'
        !currency.nullable
        columnsOf(componentOf(datastore, CompositeUserTypePayment, 'fee')).every { it.nullable }

        and: 'the table holds every column'
        tableColumnNames(datastore, CompositeUserTypePayment).containsAll(
                ['amount', 'currency', 'fee_amount', 'fee_currency', 'reference'])

        and: 'the schema reflects the column configuration'
        schemaColumn(datastore, 'COMPOSITE_USER_TYPE_PAYMENT', 'CURRENCY') == [type: 'CHARACTER', length: 3, nullable: 'NO']
        schemaColumn(datastore, 'COMPOSITE_USER_TYPE_PAYMENT', 'FEE_CURRENCY').nullable == 'YES'

        cleanup:
        datastore?.close()
    }

    void "a composite user type value is written to and read from its columns"() {
        given:
        HibernateDatastore datastore = createDatastore('compositeUserTypeRoundTrip', {}, CompositeUserTypePayment)

        when: 'an entity is saved'
        Long id = CompositeUserTypePayment.withTransaction {
            new CompositeUserTypePayment(
                    reference: 'order-1',
                    amount: new CompositeMoney(amount: 1999, currency: 'EUR'),
                    fee: new CompositeMoney(amount: 50, currency: 'EUR')
            ).save(flush: true, failOnError: true).id
        }

        then: 'the user type writes each attribute to its column'
        storedRow(datastore, id) == [amount: 1999L, currency: 'EUR', fee_amount: 50L, fee_currency: 'EUR']

        and: 'the user type reads the attributes back'
        CompositeUserTypePayment.withTransaction {
            CompositeUserTypePayment payment = CompositeUserTypePayment.get(id)
            payment.amount == new CompositeMoney(amount: 1999, currency: 'EUR') &&
                    payment.fee == new CompositeMoney(amount: 50, currency: 'EUR')
        }

        when: 'the values are replaced'
        CompositeUserTypePayment.withTransaction {
            CompositeUserTypePayment payment = CompositeUserTypePayment.get(id)
            payment.amount = new CompositeMoney(amount: 2499, currency: 'USD')
            payment.fee = null
            payment.save(flush: true, failOnError: true)
        }

        then: 'the change is written and a null value clears its columns'
        storedRow(datastore, id) == [amount: 2499L, currency: 'USD', fee_amount: null, fee_currency: null]
        CompositeUserTypePayment.withTransaction {
            CompositeUserTypePayment payment = CompositeUserTypePayment.get(id)
            payment.amount == new CompositeMoney(amount: 2499, currency: 'USD') && payment.fee == null
        }

        cleanup:
        datastore?.close()
    }

    void "a composite user type property is queried by value and by attribute"() {
        given:
        HibernateDatastore datastore = createDatastore('compositeUserTypeQuery', {}, CompositeUserTypePayment)
        CompositeMoney twenty = new CompositeMoney(amount: 2000, currency: 'EUR')
        CompositeUserTypePayment.withTransaction {
            new CompositeUserTypePayment(reference: 'ten', amount: new CompositeMoney(amount: 1000, currency: 'EUR'))
                    .save(failOnError: true)
            new CompositeUserTypePayment(reference: 'twenty', amount: twenty).save(failOnError: true)
            new CompositeUserTypePayment(reference: 'twenty-usd', amount: new CompositeMoney(amount: 2000, currency: 'USD'))
                    .save(flush: true, failOnError: true)
        }

        expect: 'HQL compares the whole value and reaches into its attributes'
        CompositeUserTypePayment.withTransaction {
            CompositeUserTypePayment.executeQuery(
                    'select p.reference from CompositeUserTypePayment p where p.amount = :amount', [amount: twenty]) == ['twenty'] &&
                    CompositeUserTypePayment.executeQuery(
                            'select p.reference from CompositeUserTypePayment p where p.amount.currency = :currency order by p.amount.amount',
                            [currency: 'EUR']) == ['ten', 'twenty']
        }

        and: 'criteria and dynamic finders compare the whole value'
        CompositeUserTypePayment.withTransaction {
            CompositeUserTypePayment.createCriteria().list { eq('amount', twenty) }*.reference == ['twenty'] &&
                    CompositeUserTypePayment.findAllByAmount(twenty)*.reference == ['twenty'] &&
                    CompositeUserTypePayment.countByAmount(twenty) == 1
        }

        cleanup:
        datastore?.close()
    }

    void "a composite user type without a column mapping names its columns after the property and the attribute"() {
        given:
        HibernateDatastore datastore = createDatastore('compositeUserTypeDefaultColumns', {}, CompositeUserTypeDefaultColumns)

        expect:
        columnNamesOf(componentOf(datastore, CompositeUserTypeDefaultColumns, 'amount')) == ['amount_amount', 'amount_currency']

        when:
        Long id = CompositeUserTypeDefaultColumns.withTransaction {
            new CompositeUserTypeDefaultColumns(amount: new CompositeMoney(amount: 5, currency: 'GBP'))
                    .save(flush: true, failOnError: true).id
        }

        then:
        CompositeUserTypeDefaultColumns.withTransaction {
            CompositeUserTypeDefaultColumns.get(id).amount == new CompositeMoney(amount: 5, currency: 'GBP')
        }

        cleanup:
        datastore?.close()
    }

    void "a composite user type registered as a user-type for its class applies without a per-property type"() {
        given: 'a datastore whose default mapping registers the composite user type for the value class'
        HibernateDatastore datastore = createDatastore('compositeUserTypeRegistered', {
            'user-type'(type: CompositeMoneyType, 'class': CompositeMoney)
        }, CompositeUserTypeRegistered)

        expect: 'the property is bound as a component with the columns of its own mapping'
        Component amount = componentOf(datastore, CompositeUserTypeRegistered, 'amount')
        amount.typeName == CompositeMoneyType.name
        columnNamesOf(amount) == ['money_value', 'money_currency']

        when:
        Long id = CompositeUserTypeRegistered.withTransaction {
            new CompositeUserTypeRegistered(amount: new CompositeMoney(amount: 7, currency: 'CHF'))
                    .save(flush: true, failOnError: true).id
        }

        then:
        CompositeUserTypeRegistered.withTransaction {
            CompositeUserTypeRegistered.get(id).amount == new CompositeMoney(amount: 7, currency: 'CHF')
        }

        cleanup:
        datastore?.close()
    }

    void "a type mapped on the property wins over a composite user type registered for its class"() {
        given: 'a datastore whose default mapping registers the composite user type, and a property mapped as serializable'
        HibernateDatastore datastore = createDatastore('compositeUserTypeRegisteredOverridden', {
            'user-type'(type: CompositeMoneyType, 'class': CompositeMoney)
        }, CompositeUserTypeRegisteredOverridden)

        expect: 'the property is one serialized column rather than a component'
        Value amount = datastore.metadata.getEntityBinding(CompositeUserTypeRegisteredOverridden.name).getProperty('amount').value
        amount instanceof BasicValue
        amount.columns*.name == ['amount']
        tableColumnNames(datastore, CompositeUserTypeRegisteredOverridden).sort() == ['amount', 'id', 'version']

        when:
        Long id = CompositeUserTypeRegisteredOverridden.withTransaction {
            new CompositeUserTypeRegisteredOverridden(amount: new CompositeMoney(amount: 9, currency: 'SEK'))
                    .save(flush: true, failOnError: true).id
        }

        then:
        CompositeUserTypeRegisteredOverridden.withTransaction {
            CompositeUserTypeRegisteredOverridden.get(id).amount == new CompositeMoney(amount: 9, currency: 'SEK')
        }

        cleanup:
        datastore?.close()
    }

    void "a unique composite user type property is unique across all of its columns"() {
        given:
        HibernateDatastore datastore = createDatastore('compositeUserTypeUnique', {}, CompositeUserTypeUnique)

        expect: 'one unique key spans the columns of the property, and no column is unique on its own'
        uniqueKeyColumns(datastore, CompositeUserTypeUnique) == [['amount_amount', 'amount_currency'] as Set]
        columnsOf(componentOf(datastore, CompositeUserTypeUnique, 'amount')).every { !it.unique }

        when: 'values that share one attribute are saved'
        CompositeUserTypeUnique.withTransaction {
            new CompositeUserTypeUnique(amount: new CompositeMoney(amount: 10, currency: 'EUR')).save(flush: true, failOnError: true)
            new CompositeUserTypeUnique(amount: new CompositeMoney(amount: 20, currency: 'EUR')).save(flush: true, failOnError: true)
            new CompositeUserTypeUnique(amount: new CompositeMoney(amount: 10, currency: 'USD')).save(flush: true, failOnError: true)
        }

        then: 'they are accepted'
        CompositeUserTypeUnique.withTransaction { CompositeUserTypeUnique.count() } == 3

        when: 'an equal complete value is validated'
        boolean valid = CompositeUserTypeUnique.withTransaction {
            new CompositeUserTypeUnique(amount: new CompositeMoney(amount: 10, currency: 'EUR')).validate()
        }

        then: 'the unique constraint rejects it'
        !valid

        when: 'an equal complete value is saved without validation'
        CompositeUserTypeUnique.withTransaction {
            new CompositeUserTypeUnique(amount: new CompositeMoney(amount: 10, currency: 'EUR')).save(flush: true, validate: false)
        }

        then: 'the database rejects it'
        thrown(DataIntegrityViolationException)

        cleanup:
        datastore?.close()
    }

    void "a composite user type property unique within a group has one key over its columns and the group"() {
        given:
        HibernateDatastore datastore = createDatastore('compositeUserTypeUniqueGroup', {}, CompositeUserTypeUniqueGroup)

        expect: 'one unique key spans the columns of the property and the column of the group'
        uniqueKeyColumns(datastore, CompositeUserTypeUniqueGroup) == [['amount_amount', 'amount_currency', 'reference'] as Set]
        columnsOf(componentOf(datastore, CompositeUserTypeUniqueGroup, 'amount')).every { !it.unique }

        when: 'values that share one attribute, or the whole value with another reference, are saved'
        CompositeUserTypeUniqueGroup.withTransaction {
            new CompositeUserTypeUniqueGroup(reference: 'a', amount: new CompositeMoney(amount: 10, currency: 'EUR'))
                    .save(flush: true, failOnError: true)
            new CompositeUserTypeUniqueGroup(reference: 'a', amount: new CompositeMoney(amount: 20, currency: 'EUR'))
                    .save(flush: true, failOnError: true)
            new CompositeUserTypeUniqueGroup(reference: 'b', amount: new CompositeMoney(amount: 10, currency: 'EUR'))
                    .save(flush: true, failOnError: true)
        }

        then: 'they are accepted'
        CompositeUserTypeUniqueGroup.withTransaction { CompositeUserTypeUniqueGroup.count() } == 3

        when: 'an equal complete value with the same reference is saved without validation'
        CompositeUserTypeUniqueGroup.withTransaction {
            new CompositeUserTypeUniqueGroup(reference: 'a', amount: new CompositeMoney(amount: 10, currency: 'EUR'))
                    .save(flush: true, validate: false)
        }

        then: 'the database rejects it'
        thrown(DataIntegrityViolationException)

        cleanup:
        datastore?.close()
    }

    void "the unique key of a composite user type property keeps the quoting and the case of its columns"() {
        given: 'a unique composite user type property whose columns are quoted: one mixed-case, one a reserved word'
        HibernateDatastore datastore = createDatastore('compositeUserTypeUniqueQuoted', {}, CompositeUserTypeUniqueQuoted)

        expect: 'the key holds the bound columns themselves, quoted, rather than copies named after them'
        Table table = datastore.metadata.getEntityBinding(CompositeUserTypeUniqueQuoted.name).table
        table.uniqueKeys.size() == 1
        List<Column> keyColumns = table.uniqueKeys.values().first().columns
        keyColumns*.name.toSet() == ['Amount', 'order'] as Set
        keyColumns.every { Column column -> column.quoted && table.getColumn(column).is(column) }

        and: 'the schema has the key on those names'
        schemaUniqueKeyColumns(datastore, 'COMPOSITE_USER_TYPE_UNIQUE_QUOTED') == [['Amount', 'order'] as Set]

        when: 'values that share one attribute are saved'
        CompositeUserTypeUniqueQuoted.withTransaction {
            new CompositeUserTypeUniqueQuoted(amount: new CompositeMoney(amount: 10, currency: 'EUR')).save(flush: true, failOnError: true)
            new CompositeUserTypeUniqueQuoted(amount: new CompositeMoney(amount: 20, currency: 'EUR')).save(flush: true, failOnError: true)
        }

        then: 'they are accepted'
        CompositeUserTypeUniqueQuoted.withTransaction { CompositeUserTypeUniqueQuoted.count() } == 2

        when: 'an equal complete value is saved without validation'
        CompositeUserTypeUniqueQuoted.withTransaction {
            new CompositeUserTypeUniqueQuoted(amount: new CompositeMoney(amount: 10, currency: 'EUR')).save(flush: true, validate: false)
        }

        then: 'the database rejects it'
        thrown(DataIntegrityViolationException)

        cleanup:
        datastore?.close()
    }

    void "a uniqueness group that names a composite user type property spans all of its columns"() {
        given: 'a composite property unique within another composite property, and a plain property unique within a composite one'
        HibernateDatastore datastore = createDatastore('compositeUserTypeUniqueComposites', {}, CompositeUserTypeUniqueComposites)
        Set<Set<String>> keys = [
                ['amount_amount', 'amount_currency', 'fee_value', 'fee_currency'] as Set,
                ['reference', 'amount_amount', 'amount_currency'] as Set
        ] as Set

        expect: 'each key holds every column of the composite properties of its group, mapped explicitly or by default'
        uniqueKeyColumns(datastore, CompositeUserTypeUniqueComposites).toSet() == keys
        schemaUniqueKeyColumns(datastore, 'COMPOSITE_USER_TYPE_UNIQUE_COMPOSITES')
                .collect { Set<String> columns -> columns*.toLowerCase() as Set }.toSet() == keys

        when: 'rows that differ in the fee, or in the reference, are saved'
        CompositeUserTypeUniqueComposites.withTransaction {
            new CompositeUserTypeUniqueComposites(reference: 'a', amount: new CompositeMoney(amount: 10, currency: 'EUR'),
                    fee: new CompositeMoney(amount: 1, currency: 'EUR')).save(flush: true, failOnError: true)
            new CompositeUserTypeUniqueComposites(reference: 'b', amount: new CompositeMoney(amount: 10, currency: 'EUR'),
                    fee: new CompositeMoney(amount: 2, currency: 'EUR')).save(flush: true, failOnError: true)
            new CompositeUserTypeUniqueComposites(reference: 'a', amount: new CompositeMoney(amount: 20, currency: 'EUR'),
                    fee: new CompositeMoney(amount: 1, currency: 'EUR')).save(flush: true, failOnError: true)
        }

        then: 'they are accepted'
        CompositeUserTypeUniqueComposites.withTransaction { CompositeUserTypeUniqueComposites.count() } == 3

        when: 'the same amount and fee are saved under another reference, without validation'
        CompositeUserTypeUniqueComposites.withTransaction {
            new CompositeUserTypeUniqueComposites(reference: 'c', amount: new CompositeMoney(amount: 10, currency: 'EUR'),
                    fee: new CompositeMoney(amount: 1, currency: 'EUR')).save(flush: true, validate: false)
        }

        then: 'the database rejects them'
        thrown(DataIntegrityViolationException)

        when: 'the same reference and amount are saved with another fee, without validation'
        CompositeUserTypeUniqueComposites.withTransaction {
            new CompositeUserTypeUniqueComposites(reference: 'a', amount: new CompositeMoney(amount: 10, currency: 'EUR'),
                    fee: new CompositeMoney(amount: 3, currency: 'EUR')).save(flush: true, validate: false)
        }

        then: 'the database rejects them'
        thrown(DataIntegrityViolationException)

        cleanup:
        datastore?.close()
    }

    void "a composite user type takes its attributes from its embeddable class when that differs from the value class"() {
        given: 'a composite user type whose embeddable class is a record describing the value class'
        HibernateDatastore datastore = createDatastore('compositeUserTypeTemplate', {}, CompositeUserTypeTemplated)

        expect: 'the attributes are those of the embeddable class'
        CompositeMoneyTemplate.isRecord()
        Component amount = componentOf(datastore, CompositeUserTypeTemplated, 'amount')
        amount.properties*.name == ['amount', 'currency']
        columnNamesOf(amount) == ['amount_amount', 'amount_currency']

        and: 'the component, and the JPA attribute, describe the value class rather than the embeddable class'
        amount.componentClassName == CompositeMoney.name
        datastore.sessionFactory.metamodel.entity(CompositeUserTypeTemplated).getAttribute('amount').javaType == CompositeMoney

        when:
        Long id = CompositeUserTypeTemplated.withTransaction {
            new CompositeUserTypeTemplated(amount: new CompositeMoney(amount: 11, currency: 'JPY'))
                    .save(flush: true, failOnError: true).id
        }

        then:
        CompositeUserTypeTemplated.withTransaction {
            CompositeUserTypeTemplated.get(id).amount == new CompositeMoney(amount: 11, currency: 'JPY')
        }

        cleanup:
        datastore?.close()
    }

    void "a composite user type whose embeddable class differs from the value class is queried by value"() {
        given:
        HibernateDatastore datastore = createDatastore('compositeUserTypeTemplateQuery', {}, CompositeUserTypeTemplated)
        CompositeMoney twenty = new CompositeMoney(amount: 2000, currency: 'EUR')
        CompositeUserTypeTemplated.withTransaction {
            new CompositeUserTypeTemplated(amount: new CompositeMoney(amount: 1000, currency: 'EUR')).save(failOnError: true)
            new CompositeUserTypeTemplated(amount: twenty).save(failOnError: true)
            new CompositeUserTypeTemplated(amount: new CompositeMoney(amount: 2000, currency: 'USD'))
                    .save(flush: true, failOnError: true)
        }

        expect: 'HQL, criteria and dynamic finders compare the whole value'
        CompositeUserTypeTemplated.withTransaction {
            CompositeUserTypeTemplated.executeQuery(
                    'select p.amount from CompositeUserTypeTemplated p where p.amount = :amount', [amount: twenty]) == [twenty] &&
                    CompositeUserTypeTemplated.createCriteria().list { eq('amount', twenty) }*.amount == [twenty] &&
                    CompositeUserTypeTemplated.findAllByAmount(twenty)*.amount == [twenty] &&
                    CompositeUserTypeTemplated.countByAmount(twenty) == 1
        }

        and: 'HQL reaches into the attributes of the embeddable class'
        CompositeUserTypeTemplated.withTransaction {
            CompositeUserTypeTemplated.executeQuery(
                    'select p.amount.amount from CompositeUserTypeTemplated p where p.amount.currency = :currency order by p.amount.amount',
                    [currency: 'EUR']) == [1000L, 2000L]
        }

        cleanup:
        datastore?.close()
    }

    void "a composite user type takes its attributes from the fields and the readable properties of its embeddable class"() {
        given: 'an immutable value class with getters only, and an embeddable class with private fields only'
        HibernateDatastore datastore = createDatastore('compositeUserTypeImmutable', {},
                CompositeUserTypeImmutable, CompositeUserTypeFieldsTemplated)

        expect: 'the final fields of the immutable value class are its attributes, less the computed property it lists in transients'
        Component price = componentOf(datastore, CompositeUserTypeImmutable, 'price')
        price.componentClassName == CompositeImmutableMoney.name
        price.properties*.name == ['amount', 'currency']
        columnNamesOf(price) == ['price_amount', 'price_currency']

        and: 'the private fields of an embeddable class without accessors are its attributes'
        Component amount = componentOf(datastore, CompositeUserTypeFieldsTemplated, 'amount')
        amount.componentClassName == CompositeMoney.name
        amount.properties*.name == ['amount', 'currency']
        columnNamesOf(amount) == ['amount_amount', 'amount_currency']

        when:
        Long priceId = CompositeUserTypeImmutable.withTransaction {
            new CompositeUserTypeImmutable(price: new CompositeImmutableMoney(1250, 'EUR'))
                    .save(flush: true, failOnError: true).id
        }
        Long amountId = CompositeUserTypeFieldsTemplated.withTransaction {
            new CompositeUserTypeFieldsTemplated(amount: new CompositeMoney(amount: 13, currency: 'NOK'))
                    .save(flush: true, failOnError: true).id
        }

        then:
        CompositeUserTypeImmutable.withTransaction {
            CompositeUserTypeImmutable.get(priceId).price == new CompositeImmutableMoney(1250, 'EUR')
        }
        CompositeUserTypeFieldsTemplated.withTransaction {
            CompositeUserTypeFieldsTemplated.get(amountId).amount == new CompositeMoney(amount: 13, currency: 'NOK')
        }

        cleanup:
        datastore?.close()
    }

    void "a composite user type whose value is a number defines each attribute column from its own configuration"() {
        given: 'a BigDecimal property stored as its scale and its unscaled value, with a column list and without one'
        HibernateDatastore datastore = createDatastore('compositeUserTypeDecimal', {}, CompositeUserTypeDecimal)

        expect: 'the property is a component rather than one numeric column'
        Component price = componentOf(datastore, CompositeUserTypeDecimal, 'price')
        price.componentClassName == BigDecimal.name
        price.properties*.name == ['scale', 'unscaledValue']
        columnNamesOf(price) == ['price_scale', 'price_unscaled']
        columnNamesOf(componentOf(datastore, CompositeUserTypeDecimal, 'discount')) == ['discount_scale', 'discount_unscaled_value']

        and: 'a numeric attribute column takes the precision of its own configuration, or the default precision of the dialect'
        columnsOf(price).last().precision == 20
        schemaNumericColumn(datastore, 'COMPOSITE_USER_TYPE_DECIMAL', 'PRICE_UNSCALED') == [type: 'NUMERIC', precision: 20, scale: 0]
        schemaNumericColumn(datastore, 'COMPOSITE_USER_TYPE_DECIMAL', 'DISCOUNT_UNSCALED_VALUE') ==
                [type: 'NUMERIC', precision: new H2Dialect().defaultDecimalPrecision, scale: 0]
        schemaNumericColumn(datastore, 'COMPOSITE_USER_TYPE_DECIMAL', 'PRICE_SCALE').type == 'INTEGER'

        when:
        Long id = CompositeUserTypeDecimal.withTransaction {
            new CompositeUserTypeDecimal(price: new BigDecimal('1234.5678'), discount: new BigDecimal('0.5'))
                    .save(flush: true, failOnError: true).id
        }

        then: 'the scale and the unscaled value are written to their columns'
        rowOf(datastore, 'composite_user_type_decimal', ['price_scale', 'price_unscaled', 'discount_scale', 'discount_unscaled_value'], id) ==
                [price_scale: 4L, price_unscaled: 12345678L, discount_scale: 1L, discount_unscaled_value: 5L]

        and: 'the value is read back with its scale'
        CompositeUserTypeDecimal.withTransaction {
            CompositeUserTypeDecimal found = CompositeUserTypeDecimal.get(id)
            found.price == new BigDecimal('1234.5678') && found.price.scale() == 4 && found.discount == new BigDecimal('0.5')
        }

        cleanup:
        datastore?.close()
    }

    void "an enum property mapped with a composite user type is bound as a component rather than as an enum column"() {
        given:
        HibernateDatastore datastore = createDatastore('compositeUserTypeEnum', {}, CompositeUserTypeEnumValued)

        expect: 'the property is a component of the user type whose class is the enum'
        Component tier = componentOf(datastore, CompositeUserTypeEnumValued, 'tier')
        tier.typeName == CompositeTierType.name
        tier.componentClassName == CompositeTier.name
        tier.properties*.name == ['code', 'rank']
        columnNamesOf(tier) == ['tier_code', 'tier_rank']
        datastore.sessionFactory.metamodel.entity(CompositeUserTypeEnumValued).getAttribute('tier').javaType == CompositeTier
        tableColumnNames(datastore, CompositeUserTypeEnumValued).sort() == ['id', 'tier_code', 'tier_rank', 'version']

        when:
        Long id = CompositeUserTypeEnumValued.withTransaction {
            new CompositeUserTypeEnumValued(tier: CompositeTier.SILVER).save(flush: true, failOnError: true).id
        }

        then: 'the user type writes the attributes of the constant and reads the constant back'
        rowOf(datastore, 'composite_user_type_enum_valued', ['tier_code', 'tier_rank'], id) == [tier_code: 'S', tier_rank: 2L]
        CompositeUserTypeEnumValued.withTransaction {
            CompositeUserTypeEnumValued.get(id).tier == CompositeTier.SILVER
        }

        and: 'the constant is queried as a whole value and by attribute'
        CompositeUserTypeEnumValued.withTransaction {
            CompositeUserTypeEnumValued.findAllByTier(CompositeTier.SILVER)*.id == [id] &&
                    CompositeUserTypeEnumValued.countByTier(CompositeTier.GOLD) == 0 &&
                    CompositeUserTypeEnumValued.executeQuery(
                            'select e.id from CompositeUserTypeEnumValued e where e.tier.rank > :rank', [rank: 1]) == [id]
        }

        cleanup:
        datastore?.close()
    }

    void "a composite user type that only a bean container creates is resolved through it for its property and for a group that names it"() {
        given: 'a user type without a default constructor, the bean container that creates it, and a plain property unique within the composite one'
        CompositeUserTypeBeanContainer.created.clear()
        HibernateDatastore datastore = createDatastore(
                ['hibernate.cdi.extensions': 'true', 'hibernate.resource.beans.container': CompositeUserTypeBeanContainer.name],
                'compositeUserTypeManagedBean', {}, CompositeUserTypeManagedBean)

        expect: 'the property is a component of the user type the container created'
        Component amount = componentOf(datastore, CompositeUserTypeManagedBean, 'amount')
        amount.typeName == CompositeManagedMoneyType.name
        columnNamesOf(amount) == ['amount_amount', 'amount_currency']

        and: 'the group of the plain property spans the columns of the composite one, through the one instance the container created'
        uniqueKeyColumns(datastore, CompositeUserTypeManagedBean) == [['reference', 'amount_amount', 'amount_currency'] as Set]
        CompositeUserTypeBeanContainer.created*.label == ['managed']

        when:
        Long id = CompositeUserTypeManagedBean.withTransaction {
            new CompositeUserTypeManagedBean(reference: 'a', amount: new CompositeMoney(amount: 10, currency: 'EUR'))
                    .save(flush: true, failOnError: true).id
        }

        then: 'that instance reads and writes the value'
        CompositeUserTypeManagedBean.withTransaction {
            CompositeUserTypeManagedBean.get(id).amount == new CompositeMoney(amount: 10, currency: 'EUR')
        }

        cleanup:
        datastore?.close()
        CompositeUserTypeBeanContainer.created.clear()
    }

    void "a composite user type without a default constructor is a startup error without a bean container"() {
        when:
        createDatastore('compositeUserTypeNoContainer', {}, CompositeUserTypeManagedBean)

        then:
        Throwable thrown = thrown()
        causeChain(thrown).find { it instanceof BeanInstantiationException }.message.contains(CompositeManagedMoneyType.name)
    }

    void "more columns than the embeddable class has attributes is a mapping error"() {
        when:
        createDatastore('compositeUserTypeTooManyColumns', {}, CompositeUserTypeTooManyColumns)

        then:
        Throwable thrown = thrown()
        MappingException cause = causeChain(thrown).find { it instanceof MappingException } as MappingException
        cause.message.contains(CompositeUserTypeTooManyColumns.name)
        cause.message.contains('amount')
        cause.message.contains('3')
        cause.message.contains('2')
    }

    private static HibernateDatastore createDatastore(String databaseName, Closure defaultMapping, Class... entities) {
        createDatastore([:], databaseName, defaultMapping, entities)
    }

    private static HibernateDatastore createDatastore(
            Map<String, Object> settings, String databaseName, Closure defaultMapping, Class... entities) {
        new HibernateDatastore(DatastoreUtils.createPropertyResolver([
                (Settings.SETTING_DB_CREATE): 'create-drop',
                'dataSource.url': "jdbc:h2:mem:${databaseName};LOCK_TIMEOUT=10000".toString(),
                'grails.gorm.default.mapping': defaultMapping
        ] + settings), entities)
    }

    private static Component componentOf(HibernateDatastore datastore, Class entity, String property) {
        datastore.metadata.getEntityBinding(entity.name).getProperty(property).value as Component
    }

    private static List<Column> columnsOf(Component component) {
        component.properties.collect { it.value.columns.first() as Column }
    }

    private static List<String> columnNamesOf(Component component) {
        columnsOf(component)*.name
    }

    private static List<String> tableColumnNames(HibernateDatastore datastore, Class entity) {
        datastore.metadata.getEntityBinding(entity.name).table.columns*.name
    }

    private static List<Set<String>> uniqueKeyColumns(HibernateDatastore datastore, Class entity) {
        datastore.metadata.getEntityBinding(entity.name).table.uniqueKeys.values().collect { it.columns*.name.toSet() }
    }

    /** The unique constraints of the table in the schema, each as the set of the names of its columns. */
    private static List<Set<String>> schemaUniqueKeyColumns(HibernateDatastore datastore, String table) {
        Map<String, Set<String>> keys = [:].withDefault { [] as Set<String> }
        datastore.dataSource.connection.withCloseable { conn ->
            conn.prepareStatement('select tc.constraint_name, ccu.column_name from information_schema.table_constraints tc ' +
                    'join information_schema.constraint_column_usage ccu on ccu.constraint_schema = tc.constraint_schema ' +
                    "and ccu.constraint_name = tc.constraint_name where tc.table_name = ? and tc.constraint_type = 'UNIQUE'")
                    .withCloseable { stmt ->
                        stmt.setString(1, table)
                        stmt.executeQuery().withCloseable { rs ->
                            while (rs.next()) {
                                keys[rs.getString('constraint_name')] << rs.getString('column_name')
                            }
                        }
                    }
        }
        keys.values().toList()
    }

    private static Map<String, Object> storedRow(HibernateDatastore datastore, Long id) {
        Map<String, Object> row = [:]
        datastore.dataSource.connection.withCloseable { conn ->
            conn.prepareStatement('select amount, currency, fee_amount, fee_currency from composite_user_type_payment where id = ?')
                    .withCloseable { stmt ->
                        stmt.setLong(1, id)
                        stmt.executeQuery().withCloseable { rs ->
                            assert rs.next()
                            ['amount', 'currency', 'fee_amount', 'fee_currency'].each { String column ->
                                Object value = rs.getObject(column)
                                row[column] = value instanceof Number ? ((Number) value).longValue() : value
                            }
                        }
                    }
        }
        row
    }

    /** The columns of the row of the entity, numbers as longs. */
    private static Map<String, Object> rowOf(HibernateDatastore datastore, String table, List<String> columns, Long id) {
        Map<String, Object> row = [:]
        datastore.dataSource.connection.withCloseable { conn ->
            conn.prepareStatement("select ${columns.join(', ')} from ${table} where id = ?".toString()).withCloseable { stmt ->
                stmt.setLong(1, id)
                stmt.executeQuery().withCloseable { rs ->
                    assert rs.next()
                    columns.each { String column ->
                        Object value = rs.getObject(column)
                        row[column] = value instanceof Number ? ((Number) value).longValue() : value
                    }
                }
            }
        }
        row
    }

    /** The data type, the precision and the scale of a numeric column in the schema. */
    private static Map<String, Object> schemaNumericColumn(HibernateDatastore datastore, String table, String column) {
        Map<String, Object> description = [:]
        datastore.dataSource.connection.withCloseable { conn ->
            conn.prepareStatement('select data_type, numeric_precision, numeric_scale from information_schema.columns ' +
                    'where table_name = ? and column_name = ?').withCloseable { stmt ->
                stmt.setString(1, table)
                stmt.setString(2, column)
                stmt.executeQuery().withCloseable { rs ->
                    assert rs.next()
                    description.type = rs.getString('data_type')
                    description.precision = rs.getObject('numeric_precision')?.with { ((Number) it).intValue() }
                    description.scale = rs.getObject('numeric_scale')?.with { ((Number) it).intValue() }
                }
            }
        }
        description
    }

    private static Map<String, Object> schemaColumn(HibernateDatastore datastore, String table, String column) {
        Map<String, Object> description = [:]
        datastore.dataSource.connection.withCloseable { conn ->
            conn.prepareStatement('select data_type, character_maximum_length, is_nullable from information_schema.columns ' +
                    'where table_name = ? and column_name = ?').withCloseable { stmt ->
                stmt.setString(1, table)
                stmt.setString(2, column)
                stmt.executeQuery().withCloseable { rs ->
                    assert rs.next()
                    description.type = rs.getString('data_type')
                    description.length = rs.getObject('character_maximum_length')?.with { ((Number) it).intValue() }
                    description.nullable = rs.getString('is_nullable')
                }
            }
        }
        description
    }

    private static List<Throwable> causeChain(Throwable throwable) {
        List<Throwable> chain = []
        for (Throwable t = throwable; t != null && !chain.contains(t); t = t.cause) {
            chain << t
        }
        chain
    }
}

@Entity
class CompositeUserTypePayment implements HibernateEntity<CompositeUserTypePayment> {
    String reference
    CompositeMoney amount
    CompositeMoney fee

    static constraints = {
        amount nullable: false
        fee nullable: true
    }

    static mapping = {
        amount type: CompositeMoneyType, {
            column name: 'amount'
            column name: 'currency', sqlType: 'char(3)'
        }
        fee type: CompositeMoneyType, {
            column name: 'fee_amount'
            column name: 'fee_currency'
        }
    }
}

@Entity
class CompositeUserTypeDefaultColumns implements HibernateEntity<CompositeUserTypeDefaultColumns> {
    CompositeMoney amount

    static mapping = {
        amount type: CompositeMoneyType
    }
}

@Entity
class CompositeUserTypeRegistered implements HibernateEntity<CompositeUserTypeRegistered> {
    CompositeMoney amount

    static mapping = {
        amount {
            column name: 'money_value'
            column name: 'money_currency'
        }
    }
}

@Entity
class CompositeUserTypeRegisteredOverridden implements HibernateEntity<CompositeUserTypeRegisteredOverridden> {
    CompositeMoney amount

    static mapping = {
        amount type: 'serializable'
    }
}

@Entity
class CompositeUserTypeUnique implements HibernateEntity<CompositeUserTypeUnique> {
    CompositeMoney amount

    static constraints = {
        amount unique: true
    }

    static mapping = {
        amount type: CompositeMoneyType
    }
}

@Entity
class CompositeUserTypeUniqueGroup implements HibernateEntity<CompositeUserTypeUniqueGroup> {
    String reference
    CompositeMoney amount

    static constraints = {
        amount unique: 'reference'
    }

    static mapping = {
        amount type: CompositeMoneyType
    }
}

@Entity
class CompositeUserTypeUniqueQuoted implements HibernateEntity<CompositeUserTypeUniqueQuoted> {
    CompositeMoney amount

    static constraints = {
        amount unique: true
    }

    static mapping = {
        amount type: CompositeMoneyType, {
            column name: '`Amount`'
            column name: '`order`'
        }
    }
}

@Entity
class CompositeUserTypeUniqueComposites implements HibernateEntity<CompositeUserTypeUniqueComposites> {
    String reference
    CompositeMoney amount
    CompositeMoney fee

    static constraints = {
        amount unique: 'fee'
        reference unique: 'amount'
    }

    static mapping = {
        amount type: CompositeMoneyType
        fee type: CompositeMoneyType, {
            column name: 'fee_value'
            column name: 'fee_currency'
        }
    }
}

@Entity
class CompositeUserTypeTemplated implements HibernateEntity<CompositeUserTypeTemplated> {
    CompositeMoney amount

    static mapping = {
        amount type: CompositeMoneyTemplateType
    }
}

@Entity
class CompositeUserTypeImmutable implements HibernateEntity<CompositeUserTypeImmutable> {
    CompositeImmutableMoney price

    static mapping = {
        price type: CompositeImmutableMoneyType
    }
}

@Entity
class CompositeUserTypeFieldsTemplated implements HibernateEntity<CompositeUserTypeFieldsTemplated> {
    CompositeMoney amount

    static mapping = {
        amount type: CompositeMoneyFieldsTemplateType
    }
}

@Entity
class CompositeUserTypeDecimal implements HibernateEntity<CompositeUserTypeDecimal> {
    BigDecimal price
    BigDecimal discount

    static mapping = {
        price type: CompositeScaledDecimalType, {
            column name: 'price_scale'
            column name: 'price_unscaled', precision: 20
        }
        discount type: CompositeScaledDecimalType
    }
}

@Entity
class CompositeUserTypeEnumValued implements HibernateEntity<CompositeUserTypeEnumValued> {
    CompositeTier tier

    static mapping = {
        tier type: CompositeTierType, {
            column name: 'tier_code', sqlType: 'char(1)'
            column name: 'tier_rank'
        }
    }
}

@Entity
class CompositeUserTypeManagedBean implements HibernateEntity<CompositeUserTypeManagedBean> {
    String reference
    CompositeMoney amount

    static constraints = {
        reference unique: 'amount'
    }

    static mapping = {
        amount type: CompositeManagedMoneyType
    }
}

@Entity
class CompositeUserTypeTooManyColumns implements HibernateEntity<CompositeUserTypeTooManyColumns> {
    CompositeMoney amount

    static mapping = {
        amount type: CompositeMoneyType, {
            column name: 'first'
            column name: 'second'
            column name: 'third'
        }
    }
}

@CompileStatic
@EqualsAndHashCode
@ToString(includeNames = true)
class CompositeMoney implements Serializable {
    long amount
    String currency
}

/** The embeddable class of {@link CompositeMoneyTemplateType}: a source of attribute metadata only. */
record CompositeMoneyTemplate(long amount, String currency) {}

/**
 * The embeddable class of {@link CompositeMoneyFieldsTemplateType}: private fields without accessors, as in the
 * {@link CompositeUserType} javadoc.
 */
@CompileStatic
class CompositeMoneyFields {
    private long amount
    private String currency
}

/**
 * An immutable value class: final fields read through getters, without setters, and a computed property that
 * is listed in {@code transients} so that it is not a column.
 */
@CompileStatic
@Immutable
class CompositeImmutableMoney implements Serializable {
    long amount
    String currency

    static transients = ['display']

    String getDisplay() {
        "${amount} ${currency}"
    }
}

/**
 * Maps {@link CompositeMoney} to two columns. The attribute indexes follow the alphabetical order of
 * the embeddable class attributes: {@code amount} is 0 and {@code currency} is 1.
 */
@CompileStatic
class CompositeMoneyType implements CompositeUserType<CompositeMoney> {

    @Override
    Object getPropertyValue(CompositeMoney component, int property) {
        switch (property) {
            case 0:
                return component.amount
            case 1:
                return component.currency
            default:
                throw new HibernateException("Unknown property index ${property}")
        }
    }

    @Override
    CompositeMoney instantiate(ValueAccess values) {
        Long amount = values.getValue(0, Long)
        String currency = values.getValue(1, String)
        if (amount == null && currency == null) {
            return null
        }
        new CompositeMoney(amount: amount, currency: currency)
    }

    @Override
    Class<?> embeddable() {
        CompositeMoney
    }

    @Override
    Class<CompositeMoney> returnedClass() {
        CompositeMoney
    }

    @Override
    boolean equals(CompositeMoney x, CompositeMoney y) {
        x == y
    }

    @Override
    int hashCode(CompositeMoney x) {
        x == null ? 0 : x.hashCode()
    }

    @Override
    CompositeMoney deepCopy(CompositeMoney value) {
        value
    }

    @Override
    boolean isMutable() {
        false
    }

    @Override
    Serializable disassemble(CompositeMoney value) {
        value
    }

    @Override
    CompositeMoney assemble(Serializable cached, Object owner) {
        (CompositeMoney) cached
    }

    @Override
    CompositeMoney replace(CompositeMoney detached, CompositeMoney managed, Object owner) {
        detached
    }
}

/** The same mapping by a user type without a default constructor, which only a bean container can create. */
@CompileStatic
class CompositeManagedMoneyType extends CompositeMoneyType {

    final String label

    CompositeManagedMoneyType(String label) {
        this.label = label
    }
}

/**
 * A Hibernate bean container that creates {@link CompositeManagedMoneyType} and leaves every other bean to the
 * fallback producer. Hibernate instantiates the container itself, from its class name.
 */
@CompileStatic
class CompositeUserTypeBeanContainer implements BeanContainer {

    static final List<CompositeManagedMoneyType> created = Collections.synchronizedList([])

    @Override
    <B> ContainedBean<B> getBean(
            Class<B> beanType, BeanContainer.LifecycleOptions lifecycleOptions, BeanInstanceProducer fallbackProducer) {
        if (beanType == CompositeManagedMoneyType) {
            CompositeManagedMoneyType userType = new CompositeManagedMoneyType('managed')
            created << userType
            return new CompositeContainedBean<B>(beanType, beanType.cast(userType))
        }
        new FallbackContainedBean<B>(beanType, fallbackProducer)
    }

    @Override
    <B> ContainedBean<B> getBean(
            String name, Class<B> beanType, BeanContainer.LifecycleOptions lifecycleOptions, BeanInstanceProducer fallbackProducer) {
        getBean(beanType, lifecycleOptions, fallbackProducer)
    }

    @Override
    void stop() {
    }
}

/** A bean the container created. */
@CompileStatic
class CompositeContainedBean<B> implements ContainedBean<B> {

    final Class<B> beanClass
    final B beanInstance

    CompositeContainedBean(Class<B> beanClass, B beanInstance) {
        this.beanClass = beanClass
        this.beanInstance = beanInstance
    }
}

/** The embeddable class of {@link CompositeScaledDecimalType}: the scale and the unscaled value of a decimal. */
record CompositeScaledDecimal(BigInteger unscaledValue, int scale) {}

/**
 * Stores a {@link BigDecimal} as its scale and its unscaled value: {@code scale} is index 0 and
 * {@code unscaledValue} is 1, in the alphabetical order of the attributes.
 */
@CompileStatic
class CompositeScaledDecimalType implements CompositeUserType<BigDecimal> {

    @Override
    Object getPropertyValue(BigDecimal component, int property) {
        switch (property) {
            case 0:
                return component.scale()
            case 1:
                return component.unscaledValue()
            default:
                throw new HibernateException("Unknown property index ${property}")
        }
    }

    @Override
    BigDecimal instantiate(ValueAccess values) {
        Integer scale = values.getValue(0, Integer)
        BigInteger unscaledValue = values.getValue(1, BigInteger)
        unscaledValue == null ? null : new BigDecimal(unscaledValue, scale ?: 0)
    }

    @Override
    Class<?> embeddable() {
        CompositeScaledDecimal
    }

    @Override
    Class<BigDecimal> returnedClass() {
        BigDecimal
    }

    @Override
    boolean equals(BigDecimal x, BigDecimal y) {
        Objects.equals(x, y)
    }

    @Override
    int hashCode(BigDecimal x) {
        x == null ? 0 : x.hashCode()
    }

    @Override
    BigDecimal deepCopy(BigDecimal value) {
        value
    }

    @Override
    boolean isMutable() {
        false
    }

    @Override
    Serializable disassemble(BigDecimal value) {
        value
    }

    @Override
    BigDecimal assemble(Serializable cached, Object owner) {
        (BigDecimal) cached
    }

    @Override
    BigDecimal replace(BigDecimal detached, BigDecimal managed, Object owner) {
        detached
    }
}

/** An enum persisted as two columns: its code and its rank. */
@CompileStatic
enum CompositeTier {
    BRONZE('B', 1), SILVER('S', 2), GOLD('G', 3)

    final String code
    final int rank

    CompositeTier(String code, int rank) {
        this.code = code
        this.rank = rank
    }

    static CompositeTier ofCode(String code) {
        values().find { CompositeTier tier -> tier.code == code }
    }
}

/** The embeddable class of {@link CompositeTierType}: {@code code} is index 0 and {@code rank} is 1. */
record CompositeTierTemplate(String code, int rank) {}

/** Maps {@link CompositeTier} to its code and its rank, and reads the constant back from the code. */
@CompileStatic
class CompositeTierType implements CompositeUserType<CompositeTier> {

    @Override
    Object getPropertyValue(CompositeTier component, int property) {
        switch (property) {
            case 0:
                return component.code
            case 1:
                return component.rank
            default:
                throw new HibernateException("Unknown property index ${property}")
        }
    }

    @Override
    CompositeTier instantiate(ValueAccess values) {
        String code = values.getValue(0, String)
        code == null ? null : CompositeTier.ofCode(code)
    }

    @Override
    Class<?> embeddable() {
        CompositeTierTemplate
    }

    @Override
    Class<CompositeTier> returnedClass() {
        CompositeTier
    }

    @Override
    boolean equals(CompositeTier x, CompositeTier y) {
        x == y
    }

    @Override
    int hashCode(CompositeTier x) {
        x == null ? 0 : x.hashCode()
    }

    @Override
    CompositeTier deepCopy(CompositeTier value) {
        value
    }

    @Override
    boolean isMutable() {
        false
    }

    @Override
    Serializable disassemble(CompositeTier value) {
        value
    }

    @Override
    CompositeTier assemble(Serializable cached, Object owner) {
        (CompositeTier) cached
    }

    @Override
    CompositeTier replace(CompositeTier detached, CompositeTier managed, Object owner) {
        detached
    }
}

/** The same mapping, described by a record rather than by the value class itself. */
@CompileStatic
class CompositeMoneyTemplateType extends CompositeMoneyType {

    @Override
    Class<?> embeddable() {
        CompositeMoneyTemplate
    }
}

/** The same mapping, described by a class with private fields and no accessors. */
@CompileStatic
class CompositeMoneyFieldsTemplateType extends CompositeMoneyType {

    @Override
    Class<?> embeddable() {
        CompositeMoneyFields
    }
}

/** Maps {@link CompositeImmutableMoney} to two columns: {@code amount} is index 0 and {@code currency} is 1. */
@CompileStatic
class CompositeImmutableMoneyType implements CompositeUserType<CompositeImmutableMoney> {

    @Override
    Object getPropertyValue(CompositeImmutableMoney component, int property) {
        switch (property) {
            case 0:
                return component.amount
            case 1:
                return component.currency
            default:
                throw new HibernateException("Unknown property index ${property}")
        }
    }

    @Override
    CompositeImmutableMoney instantiate(ValueAccess values) {
        Long amount = values.getValue(0, Long)
        String currency = values.getValue(1, String)
        if (amount == null && currency == null) {
            return null
        }
        new CompositeImmutableMoney(amount, currency)
    }

    @Override
    Class<?> embeddable() {
        CompositeImmutableMoney
    }

    @Override
    Class<CompositeImmutableMoney> returnedClass() {
        CompositeImmutableMoney
    }

    @Override
    boolean equals(CompositeImmutableMoney x, CompositeImmutableMoney y) {
        x == y
    }

    @Override
    int hashCode(CompositeImmutableMoney x) {
        x == null ? 0 : x.hashCode()
    }

    @Override
    CompositeImmutableMoney deepCopy(CompositeImmutableMoney value) {
        value
    }

    @Override
    boolean isMutable() {
        false
    }

    @Override
    Serializable disassemble(CompositeImmutableMoney value) {
        value
    }

    @Override
    CompositeImmutableMoney assemble(Serializable cached, Object owner) {
        (CompositeImmutableMoney) cached
    }

    @Override
    CompositeImmutableMoney replace(CompositeImmutableMoney detached, CompositeImmutableMoney managed, Object owner) {
        detached
    }
}
