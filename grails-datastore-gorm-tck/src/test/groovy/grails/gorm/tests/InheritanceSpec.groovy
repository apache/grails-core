package grails.gorm.tests

import grails.persistence.Entity

/**
 * @author graemerocher
 */
class InheritanceSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        return super.getDomainClasses() + [Practice]
    }

    void "Test inheritance with dynamic finder"() {

        given:
        def city = new City([code: "UK", name: "London", longitude: 49.1, latitude: 53.1])
        def country = new Country([code: "UK", name: "United Kingdom", population: 10000000])

        city.save()
        country.save(flush:true)
        session.clear()

        when:
        def locations = Location.findAllByCode("UK")
        def cities = City.findAllByCode("UK")
        def countries = Country.findAllByCode("UK")

        then:
        2 == locations.size()
        1 == cities.size()
        1 == countries.size()
        "London" == cities[0].name
        "United Kingdom" == countries[0].name
    }

    void "Test querying with inheritance"() {

        given:
        def city = new City([code: "LON", name: "London", longitude: 49.1, latitude: 53.1])
        def location = new Location([code: "XX", name: "The World"])
        def country = new Country([code: "UK", name: "United Kingdom", population: 10000000])

        country.save()
        city.save()
        location.save()

        session.flush()

        when:
        city = City.get(city.id)
        def london = Location.get(city.id)
        country = Location.findByName("United Kingdom")
        def london2 = Location.findByName("London")

        then:
        1 == City.count()
        1 == Country.count()
        3 == Location.count()

        city != null
        city instanceof City
        london instanceof City
        london2 instanceof City
        "London" == london2.name
        49.1 == london2.longitude
        "LON" == london2.code

        country instanceof Country
        "UK" == country.code
        10000000 == country.population
    }

    void "Test hasMany with inheritance should return appropriate class"() {
        given: "a practice with two locations"
        Practice practice = new Practice(name: "Test practice")
        practice.addToLocations(new City(name: "Austin", latitude: 30.2672, longitude: 97.7431))
        practice.addToLocations(new Country(name: "United States"))
        practice.save()
        session.flush()

        expect:
        Location.findByName("Austin").class == City
    }

    def clearSession() {
        City.withSession { session -> session.flush() }
    }
}

@Entity
class Practice implements Serializable {
//    Long id
    Long version
    String name
    static hasMany = [locations: Location]
}

@Entity
class Location implements Serializable {
//    Long id
    Long version
    String name
    String code = "DEFAULT"

    def namedAndCode() {
        "$name - $code"
    }

    static mapping = {
        name index:true
        code index:true
    }
}

@Entity
class City extends Location {
    BigDecimal latitude
    BigDecimal longitude
}

@Entity
class Country extends Location {
    Integer population = 0

    static hasMany = [residents:Person]
    Set residents
}