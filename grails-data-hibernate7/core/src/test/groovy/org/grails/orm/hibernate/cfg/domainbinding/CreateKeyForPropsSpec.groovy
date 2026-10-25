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

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.MappingException
import org.hibernate.mapping.Column
import org.hibernate.mapping.Table
import spock.lang.Specification

import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.CreateKeyForProps
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueKeyForColumnsCreator

class CreateKeyForPropsSpec extends Specification {

    def "creates unique key when property is unique within group"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)

        def owner = Mock(GrailsHibernatePersistentEntity)
        def grailsProp = Mock(HibernatePersistentProperty) {
            getHibernateOwner() >> owner
        }

        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["p1", "p2"]
        }
        grailsProp.getMappedForm() >> mappedForm

        def otherProp1 = Mock(HibernatePersistentProperty)
        def otherProp2 = Mock(HibernatePersistentProperty)
        owner.getHibernatePropertyByName("p1") >> otherProp1
        owner.getHibernatePropertyByName("p2") >> otherProp2

        String path = "some_path"
        def table = new Table("t")
        String baseColumnName = "base_col"

        columnNameFetcher.getColumnNamesForPropertyAndPath(otherProp1, path) >> ["col1"]
        columnNameFetcher.getColumnNamesForPropertyAndPath(otherProp2, path) >> ["col2"]

        when:
        subject.createKeyForProps(grailsProp, path, table, baseColumnName)

        then:
        1 * grailsProp.getMappedForm() >> mappedForm
        1 * grailsProp.getHibernateOwner() >> owner
        1 * mappedForm.isUnique() >> true
        1 * mappedForm.isUniqueWithinGroup() >> true
        1 * mappedForm.getUniquenessGroup() >> ["p1", "p2"]
        1 * owner.getHibernatePropertyByName("p1") >> otherProp1
        1 * owner.getHibernatePropertyByName("p2") >> otherProp2
        1 * columnNameFetcher.getColumnNamesForPropertyAndPath(otherProp1, path) >> ["col1"]
        1 * columnNameFetcher.getColumnNamesForPropertyAndPath(otherProp2, path) >> ["col2"]
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List cols -> cols*.name == ["base_col", "col1", "col2"] })
    }

    def "a group property that spans several columns contributes all of them to the key"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def compositeProp = Mock(HibernatePersistentProperty)
        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["amount"]
        }
        def grailsProp = Mock(HibernatePersistentProperty) {
            getMappedForm() >> mappedForm
            getHibernateOwner() >> owner
        }
        owner.getHibernatePropertyByName("amount") >> compositeProp
        columnNameFetcher.getColumnNamesForPropertyAndPath(compositeProp, "") >> ["amount_amount", "`Currency`"]
        def table = new Table("t")

        when:
        subject.createKeyForProps(grailsProp, "", table, "reference")

        then: "the key spans the column of the property and every column of the group property, quoted as named"
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List<Column> cols ->
            cols*.name == ["reference", "amount_amount", "Currency"] && cols*.quoted == [false, false, true]
        })
    }

    def "does nothing when property is not unique within group"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)

        def owner = Mock(GrailsHibernatePersistentEntity)
        def grailsProp = Mock(HibernatePersistentProperty) { getHibernateOwner() >> owner }

        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> false
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["p1"]
        }
        grailsProp.getMappedForm() >> mappedForm

        when:
        subject.createKeyForProps(grailsProp, null, new Table("t"), "base")

        then:
        1 * grailsProp.getMappedForm() >> mappedForm
        0 * grailsProp.getHibernateOwner() >> owner
        1 * mappedForm.isUnique() >> false
        0 * uniqueKeyCreator._
        0 * columnNameFetcher._
    }

    def "throws when uniqueness group references unknown property"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)

        def owner = Mock(GrailsHibernatePersistentEntity)
        def grailsProp = Mock(HibernatePersistentProperty) { getHibernateOwner() >> owner }
        owner.getJavaClass() >> CreateKeyForPropsSpec

        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["missingProp"]
        }
        grailsProp.getMappedForm() >> mappedForm

        owner.getHibernatePropertyByName("missingProp") >> null

        when:
        subject.createKeyForProps(grailsProp, null, new Table("t"), "base")

        then:
        thrown(MappingException)
        1 * grailsProp.getMappedForm() >> mappedForm
        1 * grailsProp.getHibernateOwner() >> owner
        1 * mappedForm.isUnique() >> true
        1 * mappedForm.isUniqueWithinGroup() >> true
        1 * mappedForm.getUniquenessGroup() >> ["missingProp"]
        1 * owner.getJavaClass() >> CreateKeyForPropsSpec
        1 * owner.getHibernatePropertyByName("missingProp")
        0 * uniqueKeyCreator._
        0 * columnNameFetcher._
    }

    def "creates no key for a collection property, whose table does not hold the other columns of the group"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)
        def collectionProp = Mock(HibernateToManyProperty) {
            isSerializableType() >> false
            isUserButNotCollectionType() >> false
        }

        when:
        subject.createKeyForProps(collectionProp, "", new Table("t_collection"), "owner_id")

        then:
        0 * collectionProp.getMappedForm()
        0 * uniqueKeyCreator._
        0 * columnNameFetcher._
    }

    def "creates the key for a serializable collection property, whose column is on the owner table"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def otherProp = Mock(HibernatePersistentProperty)
        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["x"]
        }
        def serializableProp = Mock(HibernateToManyProperty) {
            isSerializableType() >> true
            getMappedForm() >> mappedForm
            getHibernateOwner() >> owner
        }
        owner.getHibernatePropertyByName("x") >> otherProp
        columnNameFetcher.getColumnNamesForPropertyAndPath(otherProp, "") >> ["x_col"]
        def table = new Table("owner")

        when:
        subject.createKeyForProps(serializableProp, "", table, "tags")

        then:
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List cols -> cols*.name == ["tags", "x_col"] })
    }

    def "creates one key across all the columns of a unique property that spans several columns"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)
        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> false
        }
        def grailsProp = Mock(HibernatePersistentProperty) {
            getMappedForm() >> mappedForm
        }
        def table = new Table("t")
        def amount = new Column("`Amount`")
        def currency = new Column("amount_currency")

        when:
        subject.createKeyForColumns(grailsProp, "", table, [amount, currency])

        then: "the key holds the bound columns themselves, so it keeps their quoting"
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List<Column> cols ->
            cols.size() == 2 && cols[0].is(amount) && cols[1].is(currency) && cols[0].quoted
        })
        0 * columnNameFetcher._
        0 * grailsProp.getHibernateOwner()
    }

    def "creates one key across all the columns of a property and the columns of its uniqueness group"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def otherProp = Mock(HibernatePersistentProperty)
        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["reference"]
        }
        def grailsProp = Mock(HibernatePersistentProperty) {
            getMappedForm() >> mappedForm
            getHibernateOwner() >> owner
        }
        owner.getHibernatePropertyByName("reference") >> otherProp
        columnNameFetcher.getColumnNamesForPropertyAndPath(otherProp, "") >> ["reference"]
        def table = new Table("t")
        def columns = [new Column("amount_amount"), new Column("amount_currency")]

        when:
        subject.createKeyForColumns(grailsProp, "", table, columns)

        then:
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List<Column> cols ->
            cols*.name == ["amount_amount", "amount_currency", "reference"] && cols[0].is(columns[0]) && cols[1].is(columns[1])
        })

        and: "the columns of the property are left as they were"
        columns*.name == ["amount_amount", "amount_currency"]
    }

    def "a group property of a property that spans several columns contributes all of its own columns"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def feeProp = Mock(HibernatePersistentProperty)
        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["fee"]
        }
        def grailsProp = Mock(HibernatePersistentProperty) {
            getMappedForm() >> mappedForm
            getHibernateOwner() >> owner
        }
        owner.getHibernatePropertyByName("fee") >> feeProp
        columnNameFetcher.getColumnNamesForPropertyAndPath(feeProp, "") >> ["fee_value", "fee_currency"]
        def table = new Table("t")

        when:
        subject.createKeyForColumns(grailsProp, "", table, [new Column("amount_amount"), new Column("amount_currency")])

        then:
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List<Column> cols ->
            cols*.name == ["amount_amount", "amount_currency", "fee_value", "fee_currency"]
        })
    }

    def "creates no key for a property that spans several columns when it is not unique, or has no columns"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)
        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> unique
            isUniqueWithinGroup() >> false
        }
        def grailsProp = Mock(HibernatePersistentProperty) {
            getMappedForm() >> mappedForm
        }

        when:
        subject.createKeyForColumns(grailsProp, "", new Table("t"), columns)

        then:
        0 * uniqueKeyCreator._
        0 * columnNameFetcher._

        where:
        unique | columns
        false  | [new Column("amount_amount"), new Column("amount_currency")]
        true   | []
    }

    def "throws when the uniqueness group of a property that spans several columns references an unknown property"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)
        def owner = Mock(GrailsHibernatePersistentEntity) {
            getJavaClass() >> CreateKeyForPropsSpec
            getHibernatePropertyByName("missingProp") >> null
        }
        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["missingProp"]
        }
        def grailsProp = Mock(HibernatePersistentProperty) {
            getMappedForm() >> mappedForm
            getHibernateOwner() >> owner
        }

        when:
        subject.createKeyForColumns(grailsProp, "", new Table("t"), [new Column("amount_amount"), new Column("amount_currency")])

        then:
        MappingException e = thrown()
        e.message.contains("missingProp")
        0 * uniqueKeyCreator._
    }

    def "creates the key for a to-many property mapped with a user type, whose column is on the owner table"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def otherProp = Mock(HibernatePersistentProperty)
        def mappedForm = Mock(org.grails.orm.hibernate.cfg.PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["x"]
        }
        def userTypeProp = Mock(HibernateToManyProperty) {
            isSerializableType() >> false
            isUserButNotCollectionType() >> true
            getMappedForm() >> mappedForm
            getHibernateOwner() >> owner
        }
        owner.getHibernatePropertyByName("x") >> otherProp
        columnNameFetcher.getColumnNamesForPropertyAndPath(otherProp, "") >> ["x_col"]
        def table = new Table("owner")

        when:
        subject.createKeyForProps(userTypeProp, "", table, "tags")

        then:
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List cols -> cols*.name == ["tags", "x_col"] })
    }
}
