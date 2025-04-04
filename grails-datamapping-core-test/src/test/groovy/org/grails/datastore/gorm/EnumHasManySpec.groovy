package org.grails.datastore.gorm

import grails.gorm.tests.GormDatastoreSpec
import grails.persistence.Entity
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author Graeme Rocher
 */
class EnumHasManySpec extends GormDatastoreSpec{


    @Override
    List getDomainClasses() {
        [Animal]
    }

    @Issue('GRAILS-9882')
    void "Test that a collection of enums can be persisted"() {
        when:"A domain class with a collection of enum instance is saved"
            Animal zebra = new Animal(name: 'zebra')
            zebra.addToTraits(Trait.FOUR_LEGS)
            zebra.addToTraits(Trait.TAIL)
            zebra.addToTraits(Trait.STRIPES)
            zebra.save(flush:true)
            session.clear()

        then:"The results are correct"
             Animal.findByName('zebra').traits.size() == 3
    }

    void "Test removeFrom collection of enums"() {
        setup:
        Animal zebra = new Animal(name: 'zebra')
        zebra.addToTraits(Trait.FOUR_LEGS)
        zebra.addToTraits(Trait.TAIL)
        zebra.addToTraits(Trait.STRIPES)

        when:
        zebra.removeFromTraits(Trait.FOUR_LEGS)
        zebra.save(flush: true)

        then:
        zebra.traits.size() == 2
        !zebra.traits.contains(Trait.FOUR_LEGS)
        zebra.traits.contains(Trait.TAIL)
        zebra.traits.contains(Trait.STRIPES)
    }
}

@Entity
class Animal {

    Long id
    Set<Trait> traits
    static hasMany = [traits: Trait]

    String name
}

enum Trait {

    TAIL,
    FOUR_LEGS,
    SPOTS,
    STRIPES
}
