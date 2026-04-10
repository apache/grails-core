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

package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import spock.lang.Unroll

/**
 * Specification for GORM Mapping features, specifically for composite ID detection.
 */
class MappingSpec extends HibernateGormDatastoreSpec {

    @Unroll
    void "test isCompositeIdProperty should return #expectedResult for #description"() {
        given: "A persistent entity and its mapping"
        def binder = grailsDomainBinder
        // Ensure all related entities are processed by the mapping context
        createPersistentEntity(Author, binder)
        def entity = createPersistentEntity(domainClass, binder)
        def mapping = (Mapping) entity.getMappedForm()
        def property = entity.getPropertyByName(propertyName)

        when: "The method is called on the property itself"
        def resultProperty = property.isCompositeIdProperty()

        then: "The results are as expected"
        resultProperty == expectedResult

        where:
        description                               | domainClass       | propertyName | expectedResult
        "a property that is part of a composite id" | CompositeIdBook   | 'title'      | true
        "another property in the composite id"      | CompositeIdBook   | 'author'     | true
        "a property not in the composite id"        | CompositeIdBook   | 'pageCount'  | false
        "a property from a simple id class"         | SimpleIdBook      | 'title'      | false
    }

    @Unroll
    void "test isIdentityProperty should return #expectedResult for #description"() {
        given: "A persistent entity and its property"
        def binder = grailsDomainBinder
        def entity = createPersistentEntity(domainClass, binder)
        def property = entity.getPropertyByName(propertyName)

        when: "The method is called on the property itself"
        def resultProperty = property.isIdentityProperty()

        then: "The result is as expected"
        resultProperty == expectedResult

        where:
        description                        | domainClass     | propertyName | expectedResult
        "the identity property"            | SimpleIdBook    | 'id'         | true
        "a non-identity property"          | SimpleIdBook    | 'title'      | false
        "the identity in composite entity" | CompositeIdBook | 'id'         | true
        "a property in composite identity" | CompositeIdBook | 'title'      | false
    }

    // --- methodMissing dispatch tests (pure unit, no datastore) ---

    void "methodMissing dispatches Closure arg to property(name, closure)"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.firstName { column 'first_name' }

        then:
        mapping.columns['firstName'] != null
        mapping.columns['firstName'].column == 'first_name'
    }

    void "methodMissing dispatches PropertyConfig arg directly into columns map"() {
        given:
        Mapping mapping = new Mapping()
        PropertyConfig pc = new PropertyConfig()
        pc.column('first_name')

        when:
        mapping.firstName(pc)

        then:
        mapping.columns['firstName'].is(pc)
        mapping.columns['firstName'].column == 'first_name'
    }

    void "methodMissing dispatches Map arg to PropertyConfig.configureExisting"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.firstName(column: 'first_name')

        then:
        mapping.columns['firstName'] != null
        mapping.columns['firstName'].column == 'first_name'
    }

    void "methodMissing dispatches Map + Closure args — Map configures, Closure also applied"() {
        given:
        Mapping mapping = new Mapping()

        when: "Map is first arg, Closure is last arg"
        mapping.firstName([column: 'first_name'], { formula = 'UPPER(first_name)' })

        then:
        mapping.columns['firstName'] != null
        mapping.columns['firstName'].formula == 'UPPER(first_name)'
    }

    void "methodMissing throws MissingMethodException for unknown arg type"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.firstName(42)

        then:
        thrown(MissingMethodException)
    }

    // --- getOrInitializePropertyConfig (protected, same-package access) ---

    void "getOrInitializePropertyConfig creates a new PropertyConfig when none exists"() {
        given:
        Mapping mapping = new Mapping()

        when:
        PropertyConfig pc = mapping.getOrInitializePropertyConfig('age')

        then:
        pc != null
        mapping.columns['age'].is(pc)
    }

    void "getOrInitializePropertyConfig returns existing PropertyConfig when already set"() {
        given:
        Mapping mapping = new Mapping()
        PropertyConfig existing = new PropertyConfig()
        mapping.columns['age'] = existing

        when:
        PropertyConfig pc = mapping.getOrInitializePropertyConfig('age')

        then:
        pc.is(existing)
    }

    void "getOrInitializePropertyConfig clones global constraint when present"() {
        given:
        Mapping mapping = new Mapping()
        PropertyConfig global = new PropertyConfig()
        global.column('default_col')
        mapping.columns['*'] = global

        when:
        PropertyConfig pc = mapping.getOrInitializePropertyConfig('someField')

        then:
        pc != null
        !pc.is(global)                  // cloned, not the same instance
        pc.firstColumnIsColumnCopy      // single-column clone sets the flag
    }

    // --- cloneGlobalConstraint (protected, same-package access) ---

    void "cloneGlobalConstraint returns a clone with firstColumnIsColumnCopy set for single column"() {
        given:
        Mapping mapping = new Mapping()
        PropertyConfig global = new PropertyConfig()
        global.column('shared_col')
        mapping.columns['*'] = global

        when:
        PropertyConfig cloned = mapping.cloneGlobalConstraint()

        then:
        cloned != null
        !cloned.is(global)
        cloned.firstColumnIsColumnCopy
    }

    // --- PropertyConfig.checkHasSingleColumn (protected, same-package access) ---

    void "checkHasSingleColumn does not throw when only one column is configured"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.column('my_col')

        expect:
        pc.checkHasSingleColumn()  // no exception
    }

    void "checkHasSingleColumn throws when multiple columns are configured"() {
        given:
        PropertyConfig pc = new PropertyConfig()
        pc.columns << new ColumnConfig(name: 'col_a')
        pc.columns << new ColumnConfig(name: 'col_b')

        when:
        pc.checkHasSingleColumn()

        then:
        thrown(RuntimeException)
    }

    // --- table() DSL ---

    void "table(String) sets the table name on the mapping"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.table('my_table')

        then:
        mapping.tableName == 'my_table'
    }

    void "table(Closure) configures the Table via closure"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.table { name = 'closure_table' }

        then:
        mapping.tableName == 'closure_table'
    }

    void "table(Map) configures the Table via map"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.table(name: 'map_table')

        then:
        mapping.tableName == 'map_table'
    }

    void "setTableName delegates to table.name"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.tableName = 'direct_name'

        then:
        mapping.table.name == 'direct_name'
    }

    // --- id() DSL ---

    void "id(Map) configures the identity from a map"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.id(column: 'my_id_col')

        then:
        (mapping.identity as HibernateSimpleIdentity).column == 'my_id_col'
    }

    void "id(Closure) configures the identity from a closure"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.id { column = 'closure_id' }

        then:
        (mapping.identity as HibernateSimpleIdentity).column == 'closure_id'
    }

    void "id(HibernateCompositeIdentity) replaces the identity"() {
        given:
        Mapping mapping = new Mapping()
        HibernateCompositeIdentity composite = new HibernateCompositeIdentity(propertyNames: ['a', 'b'])

        when:
        mapping.id(composite)

        then:
        mapping.identity.is(composite)
    }

    // --- cache() DSL ---

    void "cache(Closure) initialises CacheConfig and applies the closure"() {
        given:
        Mapping mapping = new Mapping()
        assert mapping.cache == null

        when:
        mapping.cache { usage = CacheConfig.Usage.READ_ONLY }

        then:
        mapping.cache != null
        mapping.cache.usage == CacheConfig.Usage.READ_ONLY
    }

    void "cache(Map) initialises CacheConfig and applies the map"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.cache(usage: 'read-only')

        then:
        mapping.cache != null
    }

    void "cache(String) sets cache usage and enables caching"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.cache('read-only')

        then:
        mapping.cache != null
        mapping.cache.enabled
        mapping.cache.usage == CacheConfig.Usage.READ_ONLY
    }

    // --- sort() DSL ---

    void "sort(String, String) sets name and direction"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.sort('name', 'asc')

        then:
        mapping.sort.name == 'name'
        mapping.sort.direction == 'asc'
    }

    void "sort(Map) sets namesAndDirections"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.sort(name: 'asc', age: 'desc')

        then:
        mapping.sort.namesAndDirections == [name: 'asc', age: 'desc']
    }

    // --- discriminator() DSL ---

    void "discriminator(String) sets the discriminator value"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.discriminator('DOG')

        then:
        mapping.discriminator.value == 'DOG'
    }

    void "discriminator(Closure) applies closure to DiscriminatorConfig"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.discriminator { value = 'CAT' }

        then:
        mapping.discriminator.value == 'CAT'
    }

    void "discriminator(Map) sets value and column"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.discriminator(value: 'BIRD', column: 'disc_col')

        then:
        mapping.discriminator.value == 'BIRD'
        mapping.discriminator.column.name == 'disc_col'
    }

    // --- composite() ---

    void "composite(String...) creates a HibernateCompositeIdentity"() {
        given:
        Mapping mapping = new Mapping()

        when:
        HibernateCompositeIdentity id = mapping.composite('title', 'author')

        then:
        id instanceof HibernateCompositeIdentity
        mapping.identity.is(id)
    }

    // --- version() ---

    void "version(false) disables versioning"() {
        given:
        Mapping mapping = new Mapping()
        assert mapping.versioned

        when:
        mapping.version(false)

        then:
        !mapping.versioned
    }

    void "version(Map) configures the version column"() {
        given:
        Mapping mapping = new Mapping()

        when:
        mapping.version(column: 'ver_col')

        then:
        mapping.columns['version'] != null
        mapping.columns['version'].column == 'ver_col'
    }

    // --- isJoinedSubclass / setTablePerConcreteClass ---

    void "isJoinedSubclass returns true when tablePerHierarchy=false and tablePerConcreteClass=false"() {
        given:
        Mapping mapping = new Mapping()
        mapping.tablePerHierarchy = false

        expect:
        mapping.isJoinedSubclass()
    }

    void "isJoinedSubclass returns false when tablePerHierarchy is true"() {
        given:
        Mapping mapping = new Mapping()
        mapping.tablePerHierarchy = true

        expect:
        !mapping.isJoinedSubclass()
    }

    void "setTablePerConcreteClass(true) also sets tablePerHierarchy to false"() {
        given:
        Mapping mapping = new Mapping()
        assert mapping.tablePerHierarchy

        when:
        mapping.tablePerConcreteClass = true

        then:
        !mapping.tablePerHierarchy
        mapping.tablePerConcreteClass
    }

    // --- getTypeName ---

    void "getTypeName returns null for unknown class"() {
        given:
        Mapping mapping = new Mapping()

        expect:
        mapping.getTypeName(String) == null
    }

    void "getTypeName returns class name when mapped to a Class"() {
        given:
        Mapping mapping = new Mapping()
        mapping.userTypes[String] = Integer

        expect:
        mapping.getTypeName(String) == Integer.name
    }

    void "getTypeName returns string value when mapped to a string"() {
        given:
        Mapping mapping = new Mapping()
        mapping.userTypes[String] = 'my.custom.Type'

        expect:
        mapping.getTypeName(String) == 'my.custom.Type'
    }

    // --- hasCompositeIdentifier ---

    void "hasCompositeIdentifier returns false for default simple identity"() {
        given:
        Mapping mapping = new Mapping()

        expect:
        !mapping.hasCompositeIdentifier()
    }

    void "hasCompositeIdentifier returns true after composite() is called"() {
        given:
        Mapping mapping = new Mapping()
        mapping.composite('a', 'b')

        expect:
        mapping.hasCompositeIdentifier()
    }

    // --- configureNew / configureExisting ---

    void "configureNew(Closure) returns a new Mapping with closure applied"() {
        when:
        Mapping m = Mapping.configureNew { table 'books' }

        then:
        m != null
        m.tableName == 'books'
    }

    void "configureExisting(Mapping, Map) applies map values to existing mapping"() {
        given:
        Mapping existing = new Mapping()

        when:
        Mapping result = Mapping.configureExisting(existing, [tablePerHierarchy: false])

        then:
        result.is(existing)
        !result.tablePerHierarchy
    }

    void "configureExisting(Mapping, Closure) applies closure to existing mapping"() {
        given:
        Mapping existing = new Mapping()

        when:
        Mapping result = Mapping.configureExisting(existing, { table 'my_table' })

        then:
        result.is(existing)
        result.tableName == 'my_table'
    }

}

// --- Test Domain Classes ---
// These are top-level, non-static classes to ensure they are
// correctly discovered and processed by the GORM testing framework.

@Entity
class Author {
    String name
}

@Entity
class CompositeIdBook {
    String title
    Author author
    Integer pageCount

    static mapping = {
        id composite: ['title', 'author']
    }
}

@Entity
class SimpleIdBook {
    String title
}