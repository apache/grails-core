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

import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.hibernate.MappingException
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.hibernate.boot.spi.SecondPass
import org.hibernate.mapping.Table
import spock.lang.Specification

import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.CreateKeyForProps
import org.grails.orm.hibernate.cfg.domainbinding.util.UniqueKeyForColumnsCreator

class CreateKeyForPropsSpec extends Specification {

    InFlightMetadataCollector metadataCollector = Stub() {
        isInSecondPass() >> true
    }

    def "creates unique key when property is unique within group"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator, metadataCollector)

        def owner = Mock(GrailsHibernatePersistentEntity)
        def grailsProp = Mock(HibernatePersistentProperty) {
            getHibernateOwner() >> owner
        }

        def mappedForm = Mock(PropertyConfig) {
            isUnique() >> true
            isUniqueWithinGroup() >> true
            getUniquenessGroup() >> ["p1", "p2"]
        }
        grailsProp.getMappedForm() >> mappedForm

        def otherProp1 = Mock(HibernatePersistentProperty) {
            getHibernateMappedForm() >> new PropertyConfig()
        }
        def otherProp2 = Mock(HibernatePersistentProperty) {
            getHibernateMappedForm() >> new PropertyConfig()
        }
        owner.getHibernatePropertyByName("p1") >> otherProp1
        owner.getHibernatePropertyByName("p2") >> otherProp2

        String path = "some_path"
        def table = new Table("t")
        String baseColumnName = "base_col"

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
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(otherProp1, path, null) >> "col1"
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(otherProp2, path, null) >> "col2"
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List cols -> cols*.name == ["base_col", "col1", "col2"] })
    }

    def "expands every configured column of a unique group member in config order"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator, metadataCollector)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mappedForm = new PropertyConfig(unique: ['parent'])
        def grailsProp = Mock(HibernatePersistentProperty) {
            getMappedForm() >> mappedForm
            getHibernateOwner() >> owner
        }
        def firstColumn = new ColumnConfig(name: 'parent_code')
        def secondColumn = new ColumnConfig(name: 'parent_revision')
        def parent = Mock(HibernatePersistentProperty) {
            getHibernateMappedForm() >> new PropertyConfig(columns: [firstColumn, secondColumn])
        }
        owner.getHibernatePropertyByName('parent') >> parent
        def table = new Table('child')

        when:
        subject.createKeyForProps(grailsProp, 'child_path', table, 'child_state')

        then:
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(parent, 'child_path', firstColumn) >> 'parent_code'
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(parent, 'child_path', secondColumn) >> 'parent_revision'
        0 * columnNameFetcher._
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List columns ->
            columns*.name == ['child_state', 'parent_code', 'parent_revision']
        })
    }

    def "keeps group order when configured columns are followed by an implicit column"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator, metadataCollector)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mappedForm = new PropertyConfig(unique: ['parent', 'kind'])
        def grailsProp = Mock(HibernatePersistentProperty) {
            getMappedForm() >> mappedForm
            getHibernateOwner() >> owner
        }
        def firstColumn = new ColumnConfig(name: 'parent_code')
        def secondColumn = new ColumnConfig(name: 'parent_revision')
        def parent = Mock(HibernatePersistentProperty) {
            getHibernateMappedForm() >> new PropertyConfig(columns: [firstColumn, secondColumn])
        }
        def kind = Mock(HibernatePersistentProperty) {
            getHibernateMappedForm() >> new PropertyConfig()
        }
        owner.getHibernatePropertyByName('parent') >> parent
        owner.getHibernatePropertyByName('kind') >> kind
        def table = new Table('child')

        when:
        subject.createKeyForProps(grailsProp, null, table, 'child_state')

        then:
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(parent, null, firstColumn) >> 'parent_code'
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(parent, null, secondColumn) >> 'parent_revision'
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(kind, null, null) >> 'kind'
        0 * columnNameFetcher._
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List columns ->
            columns*.name == ['child_state', 'parent_code', 'parent_revision', 'kind']
        })
    }

    def "resolves group columns in the second pass after association columns have been populated"() {
        given:
        def collector = Mock(InFlightMetadataCollector)
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator, collector)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def mappedForm = new PropertyConfig(unique: ['parent'])
        def grailsProp = Mock(HibernatePersistentProperty) {
            getMappedForm() >> mappedForm
            getHibernateOwner() >> owner
        }
        def parentConfig = new PropertyConfig()
        def firstColumn = new ColumnConfig(name: 'parent_code')
        def secondColumn = new ColumnConfig(name: 'parent_revision')
        def parent = Mock(HibernatePersistentProperty) {
            getHibernateMappedForm() >> parentConfig
        }
        owner.getHibernatePropertyByName('parent') >> parent
        def table = new Table('child')
        SecondPass secondPass

        when:
        subject.createKeyForProps(grailsProp, null, table, 'child_state')

        then:
        1 * collector.addSecondPass(_) >> { SecondPass pass -> secondPass = pass }
        0 * columnNameFetcher._
        0 * uniqueKeyCreator._

        when:
        parentConfig.columns.addAll([firstColumn, secondColumn])
        secondPass.doSecondPass([:])

        then:
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(parent, null, { it.name == 'parent_code' }) >> 'parent_code'
        1 * columnNameFetcher.getColumnNameForPropertyAndPath(parent, null, { it.name == 'parent_revision' }) >> 'parent_revision'
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List columns ->
            columns*.name == ['child_state', 'parent_code', 'parent_revision']
        })
    }

    def "does nothing when property is not unique within group"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator, metadataCollector)

        def owner = Mock(GrailsHibernatePersistentEntity)
        def grailsProp = Mock(HibernatePersistentProperty) { getHibernateOwner() >> owner }

        def mappedForm = Mock(PropertyConfig) {
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
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator, metadataCollector)

        def owner = Mock(GrailsHibernatePersistentEntity)
        def grailsProp = Mock(HibernatePersistentProperty) { getHibernateOwner() >> owner }
        owner.getJavaClass() >> CreateKeyForPropsSpec

        def mappedForm = Mock(PropertyConfig) {
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
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator, metadataCollector)
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
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator, metadataCollector)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def otherProp = Mock(HibernatePersistentProperty) {
            getHibernateMappedForm() >> new PropertyConfig()
        }
        def mappedForm = Mock(PropertyConfig) {
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
        columnNameFetcher.getColumnNameForPropertyAndPath(otherProp, "", null) >> "x_col"
        def table = new Table("owner")

        when:
        subject.createKeyForProps(serializableProp, "", table, "tags")

        then:
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List cols -> cols*.name == ["tags", "x_col"] })
    }

    def "creates the key for a to-many property mapped with a user type, whose column is on the owner table"() {
        given:
        def columnNameFetcher = Mock(ColumnNameForPropertyAndPathFetcher)
        def uniqueKeyCreator = Mock(UniqueKeyForColumnsCreator)
        def subject = new CreateKeyForProps(columnNameFetcher, uniqueKeyCreator, metadataCollector)
        def owner = Mock(GrailsHibernatePersistentEntity)
        def otherProp = Mock(HibernatePersistentProperty) {
            getHibernateMappedForm() >> new PropertyConfig()
        }
        def mappedForm = Mock(PropertyConfig) {
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
        columnNameFetcher.getColumnNameForPropertyAndPath(otherProp, "", null) >> "x_col"
        def table = new Table("owner")

        when:
        subject.createKeyForProps(userTypeProp, "", table, "tags")

        then:
        1 * uniqueKeyCreator.createUniqueKeyForColumns(table, { List cols -> cols*.name == ["tags", "x_col"] })
    }
}
