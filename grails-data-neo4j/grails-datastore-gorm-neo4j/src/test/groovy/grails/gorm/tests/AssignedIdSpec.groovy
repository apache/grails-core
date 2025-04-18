package grails.gorm.tests

import grails.gorm.annotation.Entity
import grails.gorm.transactions.Rollback
import org.grails.datastore.gorm.neo4j.Neo4jDatastore
import org.grails.datastore.gorm.neo4j.Neo4jSession
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static grails.neo4j.mapping.MappingBuilder.node

/**
 * Created by graemerocher on 10/03/2017.
 */
class AssignedIdSpec extends Specification {

    @Shared @AutoCleanup Neo4jDatastore datastore = new Neo4jDatastore(Fruit, Origin, Farmer)

    @Rollback
    void "test save object"() {
        given:
        def coconut = new Fruit(name: "Coconut")
        Origin origin = new Origin(country: "Brazil", nationalFruit: coconut)
        coconut.origin = origin
        origin.save(flush:true)
        expect:"An object with an assigned id is saved"
        !origin.errors.hasErrors()
        new Fruit(name: "Apple", origin: origin).save()
        new Fruit(name: "Banana", origin: origin).save(flush:true)
    }
    @Rollback
    void "test save object with assigned identifier"() {
        when:"An object with an assigned id is saved"
        def coconut = new Fruit(name: "Coconut")
        Origin origin = new Origin(country: "Brazil", nationalFruit: coconut)
        coconut.origin = origin
        origin.save()
        def orange = new Fruit(name: "Orange")
        def spain = new Origin(country: "Spain", nationalFruit: orange)
        orange.origin = spain
        spain.save()
        Fruit saved = new Fruit(name: "Apple", origin: origin).save()
        new Fruit(name: "Banana", origin: origin).save(flush:true)
        origin.discard()
        saved.discard()

        then:"it exists"
        saved != null
        !saved.hasErrors()

        when:
        Fruit fruit = Fruit.get('Apple')
        then:
        fruit.id != null
        Fruit.findByName('Apple').name == "Apple"
        fruit.name == "Apple"
        fruit.origin.country == "Brazil"
        fruit.origin.fruits.size() == 3


    }

    @Rollback
    void "test save many end object with assigned identifier"() {
        when:"An object with an assigned id is saved"
        def coconut = new Fruit(name: "Coconut")
        Origin origin = new Origin(country: "Brazil", nationalFruit: coconut)
        coconut.origin = origin
                             .addToFruits(name:"Apple")
                             .addToFruits(name: "Banana")
        origin.save(flush:true)
        origin.discard()

        then:"it exists"
        origin != null
        !origin.hasErrors()

        when:
        Fruit fruit = Fruit.get('Apple')
        then:
        Fruit.findByName('Apple').name == "Apple"
        fruit.name == "Apple"
        fruit.origin.country == "Brazil"
        fruit.origin.fruits.size() == 3

    }

    @Rollback
    void "test save single end object with assigned identifier and existing object"() {
        when:"An object with an assigned id is saved"
        def coconut = new Fruit(name: "Coconut")
        Origin origin = new Origin(country: "Brazil", nationalFruit: coconut)
        coconut.origin = origin
        origin.save(flush:true)

        origin
                .addToFruits(name:"Apple")
                .addToFruits(name: "Banana")
        origin.save(flush:true)
        origin.discard()

        then:"it exists"
        origin != null
        !origin.hasErrors()

        when:
        Fruit fruit = Fruit.get('Apple')
        then:
        Fruit.findByName('Apple').name == "Apple"
        fruit.name == "Apple"
        fruit.origin.country == "Brazil"
        fruit.origin.fruits.size() == 3

    }

    @Rollback
    void "test save single end object and unidirectional"() {
        when:"An object with an assigned id is saved"
        def coconut = new Fruit(name: "Coconut")
        Origin origin = new Origin(country: "Brazil", nationalFruit: coconut)
        coconut.origin = origin
        origin.addToFarmers(name:"Bob")
        origin.save(flush:true)
        Origin.withSession { Neo4jSession session -> session.clear() }

        then:"it exists"
        origin != null
        !origin.hasErrors()


        when:
        origin = Origin.get('Brazil')
        then:
        origin.country == "Brazil"
        origin.farmers.size() == 1

    }
}

@Entity
class Origin {
    String country
    static hasMany = [fruits:Fruit, farmers:Farmer]

    Fruit nationalFruit
    static mappedBy = [fruits: 'origin']
    static mapping = node {
        id {
            generator "assigned"
            name "country"
        }
    }
}
@Entity
class Farmer {
    String name

    static mapping = node {
        id {
            generator "assigned"
            name "name"
        }
    }
}
@Entity
class Fruit {
    String name
    static belongsTo = [origin: Origin]
    static mappedBy = [origin: 'fruits']
    static mapping = node {
        origin property {
            lazy true
        }
        id {
            generator "assigned"
            name "name"
        }
    }
}
