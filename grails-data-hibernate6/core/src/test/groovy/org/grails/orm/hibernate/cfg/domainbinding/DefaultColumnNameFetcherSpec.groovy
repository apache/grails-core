package org.grails.orm.hibernate.cfg.domainbinding


import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentProperty
import spock.lang.Unroll

class DefaultColumnNameFetcherSpec extends HibernateGormDatastoreSpec {

    @Unroll
    void "Test getDefaultColumnName for #description"() {
        given:
        def namingStrategy =grailsDomainBinder.getNamingStrategy()
        def backticksRemover = new BackticksRemover()
        def fetcher = new DefaultColumnNameFetcher(namingStrategy, backticksRemover)

        // Setup related entities that might be needed by the main entity
        createPersistentEntity(AssociatedEntity, grailsDomainBinder)
        createPersistentEntity(SpecBaseEntity, grailsDomainBinder)
        createPersistentEntity(AManyToManyEntity, grailsDomainBinder) // Add the new clean
        createPersistentEntity(BManyToManyEntity, grailsDomainBinder) // A// entity
        createPersistentEntity(DefaultColumnNameFetcherSpecEntity, grailsDomainBinder)
        createPersistentEntity(InheritedEntity, grailsDomainBinder)

        def persistentEntity = createPersistentEntity(entityClass, grailsDomainBinder)
        PersistentProperty property = persistentEntity.getPropertyByName(propertyName)


        when:
        String columnName = fetcher.getDefaultColumnName(property)

        then:
        columnName == expectedColumnName

        where:
        description                          | entityClass                        | propertyName                     | expectedColumnName
        "a simple property"                  | DefaultColumnNameFetcherSpecEntity | "name"                           | "name"
        "a unidirectional one-to-many"       | DefaultColumnNameFetcherSpecUnidirectionalOwner | "children"        | "org_grails_orm_hibernate_cfg_domainbinding_default_column_name_fetcher_spec_unidirectional_owner_children_id"
        "a bidirectional many-to-one"        | DefaultColumnNameFetcherSpecEntity | "bidirectionalManyToOne"         | "bidirectional_many_to_one_id"
        "a many-to-many"                     | AManyToManyEntity                  | "manyToMany"                     | "amany_to_many_entity_id"
        "an inherited bidirectional m-t-o"   | InheritedEntity                    | "bidirectionalManyToOne"         | "bidirectional_many_to_one_id"
        "a basic collection"                 | DefaultColumnNameFetcherSpecEntity | "basicCollection"              | "default_column_name_fetcher_spec_entity_id"
        "a basic collection with type"       | DefaultColumnNameFetcherSpecEntity | "basicCollectionWithMapping"   | "basic_collection_with_mapping"
    }
}

// --- Test Domain Classes ---

@Entity
class AssociatedEntity {
    static belongsTo = [entity: SpecBaseEntity]
}

@Entity
class SpecBaseEntity {
    AssociatedEntity bidirectionalManyToOne
}

@Entity
class AManyToManyEntity {
    String name
    static hasMany = [manyToMany: BManyToManyEntity]
}


@Entity
class BManyToManyEntity {
    String name
    static hasMany = [manyToMany: AManyToManyEntity]
}

@Entity
class DefaultColumnNameFetcherSpecEntity extends SpecBaseEntity {
    String name
    List<String> basicCollection
    List<String> basicCollectionWithMapping

    static hasMany = [unidirectionalOneToMany: AssociatedEntity] // Point to the clean entity

    static mapping = {
        basicCollectionWithMapping type: 'text'
    }
}

@Entity
class InheritedEntity extends SpecBaseEntity {
    String anotherProperty
}

@Entity
class DefaultColumnNameFetcherSpecUnidirectionalChild {
    String name
}

@Entity
class DefaultColumnNameFetcherSpecUnidirectionalOwner {
    static hasMany = [children: DefaultColumnNameFetcherSpecUnidirectionalChild]
}
