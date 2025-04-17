package functional.tests

import grails.gorm.transactions.ReadOnly

class ProjectController {

    @ReadOnly
    def index() {
        respond(project: Project.list(fetch: [employees: 'join']).get(0))
    }
}
