package functional.tests

class PersonInheritanceController {

    static responseFormats = ['json']

    def index() {
        Person person = new Person(firstName: 'John', lastName: 'Doe', dob: '01/01/1970')
        respond person
    }

    def npe() {
        respond '/personinheritance/npe', model: [person: null]
    }
}
