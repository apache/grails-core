package org.grails.orm.hibernate.cfg

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import spock.lang.Unroll

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
