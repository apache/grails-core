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

import grails.gorm.tests.HibernateGormDatastoreSpec
import grails.persistence.Entity
import org.grails.datastore.mapping.model.types.Association
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.JoinTable
import org.hibernate.MappingException

import spock.lang.Unroll
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.TableForManyCalculator
import org.hibernate.boot.model.naming.Identifier
import org.hibernate.boot.model.relational.Database
import org.hibernate.boot.model.relational.Namespace
import org.hibernate.boot.spi.InFlightMetadataCollector

class TableForManyCalculatorSpec extends HibernateGormDatastoreSpec {

    @Unroll
    def "Test calculateTableForMany for #scenario"() {
        given:
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def backticksRemover = new BackticksRemover()
        def collector = Mock(InFlightMetadataCollector)
        collector.addTable(_, _, _, _, _, _, _) >> { schema, catalog, name, sub, isAbstract, context, isView ->
            return new org.hibernate.mapping.Table("test", name)
        }

        def calculator = new TableForManyCalculator(namingStrategy, collector, backticksRemover)

        GrailsHibernatePersistentEntity ownerEntityInstance
        HibernatePersistentProperty propertyToTest

        // Setup entities and properties based on scenario
        switch (scenario) {
            case "an owning OneToMany":
                ownerEntityInstance = createPersistentEntity(OwningSide)
                createPersistentEntity(AssociatedSide)
                propertyToTest = ownerEntityInstance.getPropertyByName("associated") as HibernatePersistentProperty
                break
            case "a Basic property":
                ownerEntityInstance = createPersistentEntity(BasicCollectionOwner)
                propertyToTest = ownerEntityInstance.getPropertyByName("items") as HibernatePersistentProperty
                break
            case "a Map property":
                ownerEntityInstance = createPersistentEntity(MapCollectionOwner)
                propertyToTest = ownerEntityInstance.getPropertyByName("data") as HibernatePersistentProperty
                break
            case "an owning ManyToMany":
                ownerEntityInstance = createPersistentEntity(OwningSide)
                createPersistentEntity(Tag)
                propertyToTest = ownerEntityInstance.getPropertyByName("tags") as HibernatePersistentProperty
                break
            case "an inverse ManyToMany":
                ownerEntityInstance = createPersistentEntity(Tag)
                createPersistentEntity(OwningSide)
                propertyToTest = ownerEntityInstance.getPropertyByName("owners") as HibernatePersistentProperty
                break
            case "a ManyToMany with explicit joinTable":
                ownerEntityInstance = createPersistentEntity(OwningSide)
                createPersistentEntity(Tag)
                propertyToTest = ownerEntityInstance.getPropertyByName("tags") as HibernatePersistentProperty
                propertyToTest.getMappedForm().setJoinTable(new JoinTable(name: "my_custom_join_table"))
                break
            case "a ToMany with supportsJoinColumnMapping":
                ownerEntityInstance = createPersistentEntity(UnidirectionalOwner)
                createPersistentEntity(UnidirectionalItem)
                propertyToTest = ownerEntityInstance.getPropertyByName("items") as HibernatePersistentProperty
                break
            default:
                throw new IllegalArgumentException("Unknown scenario: $scenario")
        }


        when:
        def result = calculator.calculateTableForMany(propertyToTest)

        then:
        result == expectedTableName

        where:
        scenario                              | expectedTableName

        "a Map property"                      | "map_collection_owner_data"
        "a Basic property"                    | "basic_collection_owner_items"
        "an owning OneToMany"                 | "owning_side_associated_side"
        "an owning ManyToMany"                | "tag_owners"
        "an inverse ManyToMany"               | "owning_side_tags"
        "a ManyToMany with explicit joinTable" | "my_custom_join_table"
        "a ToMany with supportsJoinColumnMapping" | "unidirectional_owner_unidirectional_item"
    }

    def "Test getTableName delegates to calculateTableForMany or uses explicit name"() {
        given:
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def collector = Mock(InFlightMetadataCollector)
        def calculator = new TableForManyCalculator(namingStrategy, collector)
        
        def ownerEntity = createPersistentEntity(OwningSide)
        def property = ownerEntity.getPropertyByName("associated") as HibernateToManyProperty

        when: "No explicit name"
        def name1 = calculator.getTableName(property)

        then:
        name1 == "owning_side_associated_side"

        when: "Explicit name"
        property.getHibernateMappedForm().setJoinTable(new JoinTable(name: "explicit_table"))
        def name2 = calculator.getTableName(property)

        then:
        name2 == "explicit_table"
    }

    def "Test getJoinTableSchema and getJoinTableCatalog"() {
        given:
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def collector = Mock(InFlightMetadataCollector)
        def database = Mock(Database)
        def namespace = Mock(Namespace)
        
        // Use a real Name since it is final
        def name = new Namespace.Name(Identifier.toIdentifier("default_catalog"), Identifier.toIdentifier("default_schema"))
        
        collector.getDatabase() >> database
        database.getDefaultNamespace() >> namespace
        namespace.getName() >> name

        def calculator = new TableForManyCalculator(namingStrategy, collector)
        
        // Mock the property to avoid needing a fully bound PersistentClass
        def table = new org.hibernate.mapping.Table("owner_table")
        table.setSchema("owner_schema")
        
        def propertyConfig = new org.grails.orm.hibernate.cfg.PropertyConfig()
        def property = Mock(HibernateToManyProperty)
        property.getTable() >> table
        property.getHibernateMappedForm() >> propertyConfig

        when: "No explicit mapping"
        def schema = calculator.getJoinTableSchema(property)
        def catalog = calculator.getJoinTableCatalog(property)

        then:
        schema == "default_schema"
        catalog == "default_catalog"

        when: "Explicit mapping"
        propertyConfig.setJoinTable(new JoinTable(schema: "explicit_schema", catalog: "explicit_catalog"))
        def schema2 = calculator.getJoinTableSchema(property)
        def catalog2 = calculator.getJoinTableCatalog(property)

        then:
        schema2 == "explicit_schema"
        catalog2 == "explicit_catalog"
    }
    def "calculateTableForMany Map property with explicit joinTable name returns joinTable name (L103)"() {
        given:
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def backticksRemover = new BackticksRemover()
        def collector = Mock(InFlightMetadataCollector)
        collector.addTable(_, _, _, _, _, _, _) >> { a, b, name, d, e, f, isView -> new org.hibernate.mapping.Table("test", name) }
        def calculator = new TableForManyCalculator(namingStrategy, collector, backticksRemover)

        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.getTableName(namingStrategy) >> "map_owner"

        def config = new PropertyConfig()
        config.setJoinTable(new JoinTable(name: "explicit_map_table"))

        def property = Mock(HibernatePersistentProperty)
        property.getName() >> "attrs"
        property.getType() >> Map.class
        property.getMappedForm() >> config
        property.getHibernateMappedForm() >> config
        property.getHibernateOwner() >> ownerEntity

        when:
        def result = calculator.calculateTableForMany(property)

        then:
        result == "explicit_map_table"
    }

    def "calculateTableForMany Map property without joinTable returns left_propName (L105)"() {
        given:
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def backticksRemover = new BackticksRemover()
        def collector = Mock(InFlightMetadataCollector)
        collector.addTable(_, _, _, _, _, _, _) >> { a, b, name, d, e, f, isView -> new org.hibernate.mapping.Table("test", name) }
        def calculator = new TableForManyCalculator(namingStrategy, collector, backticksRemover)

        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.getTableName(namingStrategy) >> "map_owner"

        def config = new PropertyConfig()  // no joinTable

        def property = Mock(HibernatePersistentProperty)
        property.getName() >> "attrs"
        property.getType() >> Map.class
        property.getMappedForm() >> config
        property.getHibernateMappedForm() >> config
        property.getHibernateOwner() >> ownerEntity

        when:
        def result = calculator.calculateTableForMany(property)

        then:
        result == "map_owner_attrs"
    }

    def "calculateTableForMany Basic property with explicit joinTable name returns joinTable name (L108)"() {
        given:
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def backticksRemover = new BackticksRemover()
        def collector = Mock(InFlightMetadataCollector)
        collector.addTable(_, _, _, _, _, _, _) >> { a, b, name, d, e, f, isView -> new org.hibernate.mapping.Table("test", name) }
        def calculator = new TableForManyCalculator(namingStrategy, collector, backticksRemover)

        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.getTableName(namingStrategy) >> "basic_owner"

        def config = new PropertyConfig()
        config.setJoinTable(new JoinTable(name: "explicit_basic_table"))

        def property = Mock(HibernateBasicProperty)
        property.getName() >> "items"
        property.getType() >> String.class
        property.getMappedForm() >> config
        property.getHibernateMappedForm() >> config
        property.getHibernateOwner() >> ownerEntity

        when:
        def result = calculator.calculateTableForMany(property)

        then:
        result == "explicit_basic_table"
    }

    def "calculateTableForMany with non-Association non-Basic property throws MappingException (L117)"() {
        given:
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def backticksRemover = new BackticksRemover()
        def collector = Mock(InFlightMetadataCollector)
        def calculator = new TableForManyCalculator(namingStrategy, collector, backticksRemover)

        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.getTableName(namingStrategy) >> "some_owner"

        def config = new PropertyConfig()  // no joinTable

        // HibernateToManyProperty is an interface; this mock is not Basic, not Association
        def property = Mock(HibernatePersistentProperty)
        property.getName() >> "unknownProp"
        property.getType() >> Object.class  // not Map
        property.getMappedForm() >> config
        property.getHibernateMappedForm() >> config
        property.getHibernateOwner() >> ownerEntity

        when:
        calculator.calculateTableForMany(property)

        then:
        thrown(MappingException)
    }

    def "calculateTableForMany with Association having null associated entity throws MappingException (L124)"() {
        given:
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def backticksRemover = new BackticksRemover()
        def collector = Mock(InFlightMetadataCollector)
        def calculator = new TableForManyCalculator(namingStrategy, collector, backticksRemover)

        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.getTableName(namingStrategy) >> "some_owner"

        def config = new PropertyConfig()

        // HibernateOneToManyProperty extends Association
        def property = Mock(HibernateOneToManyProperty)
        property.getName() >> "nullAssoc"
        property.getType() >> List.class
        property.getMappedForm() >> config
        property.getHibernateMappedForm() >> config
        property.getHibernateOwner() >> ownerEntity
        property.getAssociatedEntity() >> null  // triggers L124 throw

        when:
        calculator.calculateTableForMany(property)

        then:
        thrown(MappingException)
    }

    def "calculateTableForMany owning ManyToMany without joinTable returns left_propName (L134)"() {
        given:
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def backticksRemover = new BackticksRemover()
        def collector = Mock(InFlightMetadataCollector)
        def calculator = new TableForManyCalculator(namingStrategy, collector, backticksRemover)

        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.getTableName(namingStrategy) >> "left_entity"

        def assocEntity = Mock(GrailsHibernatePersistentEntity)
        assocEntity.getTableName(namingStrategy) >> "right_entity"

        def config = new PropertyConfig()  // no joinTable → hasJoinTableMapping = false

        def property = Mock(HibernateManyToManyProperty)
        property.getName() >> "rightItems"
        property.getType() >> List.class
        property.getMappedForm() >> config
        property.getHibernateMappedForm() >> config
        property.getHibernateOwner() >> ownerEntity
        property.getAssociatedEntity() >> assocEntity
        property.isOwningSide() >> true  // triggers L134

        when:
        def result = calculator.calculateTableForMany(property)

        then:
        result == "left_entity_right_items"
    }

    def "calculateTableForMany supportsJoinColumnMapping with explicit joinTable returns joinTable name (L142)"() {
        given:
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def backticksRemover = new BackticksRemover()
        def collector = Mock(InFlightMetadataCollector)
        def calculator = new TableForManyCalculator(namingStrategy, collector, backticksRemover)

        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.getTableName(namingStrategy) >> "left_entity"

        def assocEntity = Mock(GrailsHibernatePersistentEntity)
        assocEntity.getTableName(namingStrategy) >> "right_entity"

        def config = new PropertyConfig()
        config.setJoinTable(new JoinTable(name: "explicit_join_table"))

        // HibernateOneToManyProperty supports join column mapping when unidirectional
        def property = Mock(HibernateOneToManyProperty)
        property.getName() >> "items"
        property.getType() >> List.class
        property.getMappedForm() >> config
        property.getHibernateMappedForm() >> config
        property.getHibernateOwner() >> ownerEntity
        property.getAssociatedEntity() >> assocEntity
        property.supportsJoinColumnMapping() >> true  // triggers L140-L142

        when:
        def result = calculator.calculateTableForMany(property)

        then:
        result == "explicit_join_table"
    }

    def "calculateTableForMany non-owning Association returns right_left (L150)"() {
        given:
        def namingStrategy = getGrailsDomainBinder().getNamingStrategy()
        def backticksRemover = new BackticksRemover()
        def collector = Mock(InFlightMetadataCollector)
        def calculator = new TableForManyCalculator(namingStrategy, collector, backticksRemover)

        def ownerEntity = Mock(GrailsHibernatePersistentEntity)
        ownerEntity.getTableName(namingStrategy) >> "left_entity"

        def assocEntity = Mock(GrailsHibernatePersistentEntity)
        assocEntity.getTableName(namingStrategy) >> "right_entity"

        def config = new PropertyConfig()  // no joinTable

        def property = Mock(HibernateOneToManyProperty)
        property.getName() >> "items"
        property.getType() >> List.class
        property.getMappedForm() >> config
        property.getHibernateMappedForm() >> config
        property.getHibernateOwner() >> ownerEntity
        property.getAssociatedEntity() >> assocEntity
        property.supportsJoinColumnMapping() >> false
        property.isOwningSide() >> false  // triggers L150

        when:
        def result = calculator.calculateTableForMany(property)

        then:
        result == "right_entity_left_entity"
    }
}

@Entity
class AssociatedSide {
    static belongsTo = [owningSide: OwningSide]
}

@Entity
class OwningSide {
    static hasMany = [associated: AssociatedSide, tags: Tag]
    static mappedBy = [tags: 'owners']
}

@Entity
class BasicCollectionOwner {
    java.util.List<String> items
}


@Entity
class MapCollectionOwner {
    java.util.List<String> data
}

@Entity
class Tag {
    static hasMany = [owners: OwningSide]
}

@Entity
class UnidirectionalItem {
}

@Entity
class UnidirectionalOwner {
    static hasMany = [items: UnidirectionalItem]
}
