package demo

import grails.gorm.services.Service

@Service(Person)
abstract class PersonService {
    abstract List<Person> list()
}
