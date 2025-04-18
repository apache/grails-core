package grails.gorm.tests

/**
 * Created by graemerocher on 27/07/2016.
 */
class FindByIsNullSpec extends GormDatastoreSpec {

    @Override
    List getDomainClasses() {
        [Pet]
    }

    void "Test find by is null"() {
        given:
        new Pet(name: "foo", age: 10).save(flush:true, failOnError:true)
        new Pet(name: "bar", age: null).save(flush:true, failOnError:true)
        new Pet(name: "", age: 12).save(flush:true, failOnError:true)
        session.clear()

        expect:
        Pet.findAllByAge(null).size() == 1
        Pet.findAllByAgeIsNull().size() == 1
        Pet.count()  == 3
        !Pet.findByNameIsNull()
        !Pet.findByName(null)
        Pet.findAllByAgeIsNotNull().size() == 2
        Pet.findAllByNameIsEmpty().size() == 1
        Pet.findAllByNameIsNotEmpty().size() == 2
    }
}
