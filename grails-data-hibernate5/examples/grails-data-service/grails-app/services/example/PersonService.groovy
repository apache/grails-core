package example

import grails.gorm.services.Service

@Service(Person)
abstract class PersonService {

    abstract Person save(String firstName, String lastName)

}
