package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.util.NamingStrategyWrapper
import spock.lang.Unroll
import org.hibernate.mapping.Property
import org.hibernate.mapping.ManyToOne
import org.hibernate.mapping.Table

class GrailsHibernatePersistentPropertySpec extends HibernateGormDatastoreSpec {

    @Unroll
    void "test isEnumType for property #propertyName"() {
        given:
        PersistentEntity entity = createPersistentEntity(TestEntityWithEnum)
        GrailsHibernatePersistentProperty property = (GrailsHibernatePersistentProperty) entity.getPropertyByName(propertyName)

        expect:
        property.isEnumType() == expected

        where:
        propertyName | expected
        "myEnum"     | true
        "name"       | false
    }

    @Unroll
    void "test association checks for property #propertyName"() {
        given:
        createPersistentEntity(AssociatedEntity)
        createPersistentEntity(ManyToOneEntity)
        PersistentEntity entity = createPersistentEntity(TestEntityWithAssociations)
        GrailsHibernatePersistentProperty property = (GrailsHibernatePersistentProperty) entity.getPropertyByName(propertyName)

        expect:
        property.isOneToOne() == isOneToOne
        property.isManyToOne() == isManyToOne

        where:
        propertyName | isOneToOne | isManyToOne
        "oneToOne"   | true       | false
        "manyToOne"  | false      | true
    }
    
    void "test isUserButNotCollectionType"() {
        given:
        PersistentEntity entity = createPersistentEntity(TestEntityWithEnum)
        GrailsHibernatePersistentProperty property = (GrailsHibernatePersistentProperty) entity.getPropertyByName("myEnum")
        
        expect:
        !property.isUserButNotCollectionType()
    }

    void "test isSerializableType"() {
        given:
        PersistentEntity entity = createPersistentEntity(TestEntityWithSerializable)
        GrailsHibernatePersistentProperty property = (GrailsHibernatePersistentProperty) entity.getPropertyByName("payload")

        expect:
        property.isSerializableType()
    }

    void "test isEmbedded() for embedded property"() {
        given:
        PersistentEntity entity = createPersistentEntity(TestEntityWithEmbedded)
        GrailsHibernatePersistentProperty property = (GrailsHibernatePersistentProperty) entity.getPropertyByName("address")

        expect:
        property.isEmbedded()
    }

    void "test getTypeName()"() {
        given:
        PersistentEntity entity = createPersistentEntity(TestEntityWithTypeName)
        GrailsHibernatePersistentProperty property = (GrailsHibernatePersistentProperty) entity.getPropertyByName("name")

        expect:
        property.getTypeName() == "string"
    }

    void "test getIndexColumnType()"() {
        given:
        createPersistentEntity(MapValue)
        PersistentEntity entityWithDefaultMap = createPersistentEntity(EntityWithMap)
        PersistentEntity entityWithCustomMap = createPersistentEntity(EntityWithCustomMapIndex)
        PersistentEntity entityWithList = createPersistentEntity(EntityWithList)

        GrailsHibernatePersistentProperty defaultMapProp = (GrailsHibernatePersistentProperty) entityWithDefaultMap.getPropertyByName("tags")
        GrailsHibernatePersistentProperty customMapProp = (GrailsHibernatePersistentProperty) entityWithCustomMap.getPropertyByName("tags")
        GrailsHibernatePersistentProperty listProp = (GrailsHibernatePersistentProperty) entityWithList.getPropertyByName("items")

        expect:
        defaultMapProp.getIndexColumnType("string") == "string"
        customMapProp.getIndexColumnType("long") == "long"
        listProp.getIndexColumnType("integer") == "integer"
    }

    void "test isHibernateOneToOne and isHibernateManyToOne"() {
        given:
        createPersistentEntity(AssociatedEntity)
        createPersistentEntity(ManyToOneEntity)
        PersistentEntity entity = createPersistentEntity(TestEntityWithAssociations)
        GrailsHibernatePersistentProperty oneToOneProp = (GrailsHibernatePersistentProperty) entity.getPropertyByName("oneToOne")
        GrailsHibernatePersistentProperty manyToOneProp = (GrailsHibernatePersistentProperty) entity.getPropertyByName("manyToOne")

        expect:
        oneToOneProp.isHibernateOneToOne()
        !oneToOneProp.isHibernateManyToOne()
        !manyToOneProp.isHibernateOneToOne()
        manyToOneProp.isHibernateManyToOne()
    }



    @Unroll
    void "test isBidirectionalManyToOneWithListMapping for property #propertyName"() {
        given:
        createPersistentEntity(BMTOWLMBook)
        createPersistentEntity(BMTOWLMAuthor)
        PersistentEntity entity = createPersistentEntity(BMTOWLMAuthor)
        GrailsHibernatePersistentProperty property = (GrailsHibernatePersistentProperty) entity.getPropertyByName(propertyName)

        // Add this for the 'prop' parameter
        Property mockProperty = Mock(Property)
        ManyToOne mockManyToOne = GroovyMock(ManyToOne)
        mockProperty.getValue() >> mockManyToOne

        when:
        boolean isBidirectional = property.isBidirectionalManyToOneWithListMapping(mockProperty)

        then:
        isBidirectional == expectedIsBidirectional

        where:
        propertyName | expectedIsBidirectional
        "books"      | true
        "name"       | false
    }


    void "test getIndexColumnName and getMapElementName"() {
        given:
        def jdbcEnvironment = Mock(org.hibernate.engine.jdbc.env.spi.JdbcEnvironment)
        def namingStrategy = new NamingStrategyWrapper(new org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl(), jdbcEnvironment)
        PersistentEntity entityWithList = createPersistentEntity(EntityWithList)
        PersistentEntity entityWithMap = createPersistentEntity(EntityWithMap)

        GrailsHibernatePersistentProperty listProp = (GrailsHibernatePersistentProperty) entityWithList.getPropertyByName("items")
        GrailsHibernatePersistentProperty mapProp = (GrailsHibernatePersistentProperty) entityWithMap.getPropertyByName("tags")

        expect:
        listProp.getIndexColumnName(namingStrategy) == "items_idx"
        mapProp.getMapElementName(namingStrategy) == "tags_elt"
    }

    void "test getTypeName(SimpleValue) and getTypeParameters(SimpleValue)"() {
        given:
        def domainBinder = getGrailsDomainBinder()
        def metadataBuildingContext = domainBinder.getMetadataBuildingContext()
        def table = new Table("TEST")
        PersistentEntity entity = createPersistentEntity(TestEntityWithTypeName)
        GrailsHibernatePersistentProperty property = (GrailsHibernatePersistentProperty) entity.getPropertyByName("name")
        
        def sv = new org.hibernate.mapping.BasicValue(metadataBuildingContext, table)
        
        expect:
        property.getTypeName(sv) == "string"
        property.getTypeParameters(sv) == null // No type params in TestEntityWithTypeName
    }

    void "test getTypeName(SimpleValue) with fallback"() {
        given:
        def domainBinder = getGrailsDomainBinder()
        def metadataBuildingContext = domainBinder.getMetadataBuildingContext()
        def table = new Table("TEST2")
        PersistentEntity entity = createPersistentEntity(TestEntityWithEnum)
        GrailsHibernatePersistentProperty property = (GrailsHibernatePersistentProperty) entity.getPropertyByName("name")
        
        def sv = new org.hibernate.mapping.BasicValue(metadataBuildingContext, table)
        
        expect:
        property.getTypeName(sv) == String.name
    }

    void "test getTypeName(SimpleValue) for DependantValue"() {
        given:
        def domainBinder = getGrailsDomainBinder()
        def metadataBuildingContext = domainBinder.getMetadataBuildingContext()
        def table = new Table("TEST3")
        PersistentEntity entity = createPersistentEntity(BMTOWLMAuthor)
        GrailsHibernatePersistentProperty property = (GrailsHibernatePersistentProperty) entity.getPropertyByName("books")
        
        // DependantValue usually represents a foreign key, it should use the identity type of the owner
        def dv = new org.hibernate.mapping.DependantValue(metadataBuildingContext, table, new org.hibernate.mapping.BasicValue(metadataBuildingContext, table))
        
        expect:
        property.getTypeName(dv) == Long.name // Author's ID is Long
    }

    void "test validateAssociation throws exception for user type"() {
        given:
        PersistentEntity entity = createPersistentEntity(TestEntityWithAssociations)
        GrailsHibernatePersistentProperty prop = (GrailsHibernatePersistentProperty) entity.getPropertyByName("manyToOne")
        
        // Mocking getUserType to return a non-null value for an association
        def proxyProp = Spy(prop)
        proxyProp.getUserType() >> String.class

        when:
        proxyProp.validateAssociation()

        then:
        thrown(org.hibernate.MappingException)
    }
}


enum TestEnum {
    A, B
}

@Entity
class TestEntityWithEnum {
    Long id
    String name
    TestEnum myEnum
}

@Entity
class TestEntityWithTypeName {
    Long id
    String name
    static mapping = {
        name type: 'string'
    }
}

@Entity
class TestEntityWithAssociations {
    Long id
    String name
    AssociatedEntity oneToOne
    ManyToOneEntity manyToOne
    
    static hasOne = [oneToOne: AssociatedEntity]
}

@Entity
class AssociatedEntity {
    Long id
    String name
    TestEntityWithAssociations parent
    
    static belongsTo = [parent: TestEntityWithAssociations]
}

@Entity
class ManyToOneEntity {
    Long id
    String name
    static hasMany = [entities: TestEntityWithAssociations]
}

@Entity
class TestEntityWithSerializable {
    Long id
    byte[] payload
    static mapping = {
        payload type: 'serializable'
    }
}

@Entity

class TestEntityWithEmbedded {

    Long id

    Address address

    static embedded = ['address']

}



@Entity
class Address {

    String city

}

@Entity
class EntityWithMap {
    Long id
    Map<String, MapValue> tags
    static hasMany = [tags: MapValue]
}

@Entity
class MapValue {
    Long id
    String name
}

@Entity
class EntityWithCustomMapIndex {
    Long id
    Map<Long, MapValue> tags
    static hasMany = [tags: MapValue]
    static mapping = {
        tags indexColumn: [type: 'long']
    }
}

@Entity
class EntityWithList {
    Long id
    List<String> items
    static hasMany = [items: String]
}

@Entity
class BaseTPH {
    Long id
    static mapping = {
        tablePerHierarchy true
    }
}

@Entity
class SubTPH extends BaseTPH {
    String subProp
}

@Entity
class BaseTablePerClass {
    Long id
    static mapping = {
        tablePerHierarchy false
    }
}

@Entity
class SubTablePerClass extends BaseTablePerClass {
    String subProp
}

@Entity
class BMTOWLMBook {
    Long id
    String title
    BMTOWLMAuthor author

    static belongsTo = [author: BMTOWLMAuthor]
}

@Entity
class BMTOWLMAuthor {
    Long id
    String name
    List<BMTOWLMBook> books

    static hasMany = [books: BMTOWLMBook]
}
