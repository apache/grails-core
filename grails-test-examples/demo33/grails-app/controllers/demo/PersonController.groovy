package demo

class PersonController {

    def index() {
        [people: Person.list()]
    }
}
