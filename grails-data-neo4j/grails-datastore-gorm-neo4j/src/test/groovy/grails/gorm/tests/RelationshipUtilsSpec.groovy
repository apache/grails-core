package grails.gorm.tests

import org.grails.datastore.gorm.neo4j.RelationshipUtils

/**
 * Created by stefan on 14.04.14.
 */
class RelationshipUtilsSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Person, Pet]
    }

    def "unidirectional associations are never reversed"() {

        setup:
            def person = session.mappingContext.getPersistentEntity(Person.class.name)
            def pets = person.getPropertyByName("pets")
            def pet = session.mappingContext.getPersistentEntity(Pet.class.name)
            def owner = pet.getPropertyByName("owner")

        expect:
            pets.bidirectional
            owner.bidirectional
            RelationshipUtils.useReversedMappingFor(pets) == false
            RelationshipUtils.useReversedMappingFor(owner) == true
    }


}
