import testapp.Person

databaseChangeLog = {
    changeSet(id: 'create-person-grails', author: 'integration-test') {

        grailsChange {
            change {
                Person person = new Person()
                person.firstName = 'Joseph1'
                person.lastName = 'Holmes'
                person.age = 56
                person.gender = 'male'
                person.cell = '734-776-7738'
                person.emailAddress = 'jhomes@example.com'
                person.save(flush: true, failOnError: true)
            }
            rollback {
                confirm('Done: Rollback person Jone')
            }
        }
    }
}