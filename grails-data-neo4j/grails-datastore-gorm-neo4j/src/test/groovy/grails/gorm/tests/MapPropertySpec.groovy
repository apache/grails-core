package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.neo4j.Neo4jEntity
import spock.lang.Ignore

/**
 * Created by graemerocher on 09/09/2016.
 */
@Ignore
class MapPropertySpec extends GormDatastoreSpec {

    @Ignore
    void "Test persist map property"() {
        when:"a object with a map property is persisted"

        new Animal(name: 'Dog', attributes: [legs:4]).save(flush:true)
                                                     .discard()
        Animal a = Animal.first()

        then:
        a.name == "Dog"
        a.attributes.size() == 1
    }

    @Override
    List getDomainClasses() {
        [Animal]
    }
}

@Entity
class Animal implements Neo4jEntity<Animal> {
    String name
    Map attributes
}
