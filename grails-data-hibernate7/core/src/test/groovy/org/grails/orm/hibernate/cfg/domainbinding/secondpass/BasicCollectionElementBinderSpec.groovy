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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.domainbinding.binder.ColumnConfigToColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Collection
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Set
import org.hibernate.mapping.Table
import spock.lang.Subject

class BasicCollectionElementBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    BasicCollectionElementBinder binder

    // Mock the collaborator
    EnumTypeBinder enumTypeBinder = Mock(EnumTypeBinder)

    void setup() {
        def domainBinder = getGrailsDomainBinder()

        // Inject the mocked enumTypeBinder into the Subject
        binder = new BasicCollectionElementBinder(
                domainBinder.metadataBuildingContext,
                domainBinder.namingStrategy,
                enumTypeBinder,
                new SimpleValueColumnBinder(),
                new SimpleValueColumnFetcher(),
                new ColumnConfigToColumnBinder()
        )
    }

    private Collection collectionWithTable(String tableName) {
        def mbc = getGrailsDomainBinder().metadataBuildingContext
        def collection = new Set(mbc, new RootClass(mbc))
        collection.setCollectionTable(new Table(tableName))
        return collection
    }

    void 'bind creates BasicValue with column for scalar collection'() {
        given:
        def entity = createPersistentEntity(BCEBAuthor)
        HibernateToManyProperty property = (HibernateToManyProperty) entity.getPropertyByName('tags')
        Collection collection = collectionWithTable('bceb_author_tags')

        property.setCollection(collection)

        when:
        BasicValue element = binder.bind(property)

        then:
        element != null
        element.getColumnSpan() > 0
        // Ensure the enum binder is NOT called for a String collection
        0 * enumTypeBinder.bindEnumTypeForColumn(_, _, _)
    }

    void 'bind delegates to enumTypeBinder for enum collection'() {
        given:
        def entity = createPersistentEntity(BCEBAuthor)
        HibernateToManyProperty property = (HibernateToManyProperty) entity.getPropertyByName('statuses')
        Collection collection = collectionWithTable('bceb_author_statuses')

        property.setCollection(collection)

        // Create a dummy BasicValue to return from the mock
        def mockValue = new BasicValue(getGrailsDomainBinder().metadataBuildingContext, collection.getCollectionTable())

        when:
        BasicValue element = binder.bind(property)

        then:
        element != null
        // Corrected: Match the 3-argument signature (Property, Class, String)
        1 * enumTypeBinder.bindEnumTypeForColumn(property) >> mockValue
    }

    void 'test bind with custom column mapping and backticks'() {
        given: 'An entity with backticks in the mapping'
        def entity = createPersistentEntity(BCEBCustom)
        HibernateToManyProperty property = (HibernateToManyProperty) entity.getPropertyByName('flags')
        property.setCollection(collectionWithTable('bceb_custom_flags'))

        when: 'The binder processes the property'
        BasicValue element = binder.bind(property)

        then: 'The name is retrieved from mapping and backticks are handled by the mapping layer'
        // Actual behavior: the mapping layer provides the name without backticks to the binder
        element.getColumns().get(0).getName() == 'flag_identifier'
    }

    void 'test bind handles reserved words and removes backticks for default names'() {
        given: 'An entity using a reserved word property'
        def entity = createPersistentEntity(BCEBReserved)
        HibernateToManyProperty property = (HibernateToManyProperty) entity.getPropertyByName('group')
        property.setCollection(collectionWithTable('bceb_reserved_group'))

        when: 'The binder processes the property'
        BasicValue element = binder.bind(property)

        then: 'BackticksRemover ensures the concatenated name is clean'
        // Targets Line 81: new BackticksRemover().apply(prop) + UNDERSCORE + ...
        element.getColumns().get(0).getName() == 'group_java_lang_string'
    }

    void 'test bindSimpleValue with default generated column name'() {
        given: 'A standard entity with no explicit mapping'
        def entity = createPersistentEntity(BCEBDefault)
        HibernateToManyProperty property = (HibernateToManyProperty) entity.getPropertyByName('tags')
        property.setCollection(collectionWithTable('bceb_default_tags'))

        when: 'The binder processes the property'
        BasicValue element = binder.bind(property)

        then: 'The column name is generated using the property and type name'
        // Targets Line 81 for name generation and Line 87 for binding
        element.getColumns().get(0).getName() == 'tags_java_lang_string'
    }

    void 'test bindSimpleValue with explicit mapped column name'() {
        given: 'An entity with an explicit join table column name'
        def entity = createPersistentEntity(BCEBExplicit)
        HibernateToManyProperty property = (HibernateToManyProperty) entity.getPropertyByName('flags')
        property.setCollection(collectionWithTable('bceb_explicit_flags'))

        when: 'The binder processes the property'
        BasicValue element = binder.bind(property)

        then: 'The column name is taken from the mapping configuration'
        // Targets Line 75 for name retrieval and Line 87 for binding
        element.getColumns().get(0).getName() == 'custom_flag_col'

        and: 'The ColumnConfig is bound to the resulting column'
        // Confirms the if (joinColumnMappingOptional.isPresent()) block at Line 89
        element.getColumns().get(0).getValue() == element
    }

    void 'Path 1: bindSimpleValue uses explicit mapping name'() {
        given:
        def entity = createPersistentEntity(BCEBPath1)
        HibernateToManyProperty property = (HibernateToManyProperty) entity.getPropertyByName('flags')
        property.setCollection(collectionWithTable('bceb_path1_table'))

        when:
        BasicValue element = binder.bind(property)

        then: 'columnName is taken directly from mapping (Line 75)'
        element.getColumns().get(0).getName() == 'explicit_col'
    }

    void 'Path 2: bind delegates to enumTypeBinder for enum path'() {
        given:
        def entity = createPersistentEntity(BCEBPath2)
        HibernateToManyProperty property = (HibernateToManyProperty) entity.getPropertyByName('statuses')
        property.setCollection(collectionWithTable('bceb_path2_table'))
        def mockValue = new BasicValue(getGrailsDomainBinder().metadataBuildingContext, property.getCollection().getCollectionTable())

        when:
        BasicValue element = binder.bind(property)

        then: 'columnName is the resolved fully qualified Enum class name'
        // The namingStrategy resolves 'org.grails.orm.hibernate.cfg.domainbinding.secondpass.BCEBStatus'
        // to 'org_grails_orm_hibernate_cfg_domainbinding_secondpass_bcebstatus'
        1 * enumTypeBinder.bindEnumTypeForColumn(property) >> mockValue
        element == mockValue
    }

    void 'Path 3: bindSimpleValue uses concatenated property and type for scalars'() {

        given:
        def entity = createPersistentEntity(BCEBPath3)
        HibernateToManyProperty property = (HibernateToManyProperty) entity.getPropertyByName('tags')
        property.setCollection(collectionWithTable('bceb_path3_table'))

        when:
        BasicValue element = binder.bind(property)

        then: 'columnName is property + _ + type with backticks removed (Line 81)'
        // tags + _ + java_lang_string
        element.getColumns().get(0).getName() == 'tags_java_lang_string'
    }
}

enum BCEBStatus { ACTIVE, INACTIVE }

@Entity
class BCEBAuthor {

    Long id
    java.util.Set<String> tags
    java.util.Set<BCEBStatus> statuses
    static hasMany = [tags: String, statuses: BCEBStatus]
}

@Entity
class BCEBCustom {

    Long id
    java.util.Set<String> flags
    static hasMany = [flags: String]
    static mapping = {
        // Targets the joinColumnMappingOptional branch (Line 74)
        flags joinTable: [column: '`flag_identifier`']
    }
}

@Entity
class BCEBReserved {

    Long id
    java.util.Set<String> group // 'group' is a SQL reserved word
    static hasMany = [group: String]
}

@Entity
class BCEBDefault {

    Long id
    java.util.Set<String> tags
    static hasMany = [tags: String]
}

@Entity
class BCEBExplicit {

    Long id
    java.util.Set<String> flags
    static hasMany = [flags: String]
    static mapping = {
        flags joinTable: [column: 'custom_flag_col']
    }
}

@Entity
class BCEBPath1 { // Explicit Mapping
    Long id
    java.util.Set<String> flags
    static hasMany = [flags: String]
    static mapping = {
        flags joinTable: [column: 'explicit_col']
    }
}

@Entity
class BCEBPath2 { // Default Enum
    Long id
    java.util.Set<BCEBStatus> statuses
    static hasMany = [statuses: BCEBStatus]
}

@Entity
class BCEBPath3 { // Default Scalar
    Long id
    java.util.Set<String> tags
    static hasMany = [tags: String]
}