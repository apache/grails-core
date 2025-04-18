package grails.test.app

import groovy.transform.CompileStatic

@CompileStatic
class BootStrap {

    DogService dogService
    LabradoodleService labradoodleService
    HumanService humanService
    GrailsTeamMemberService grailsTeamMemberService

    def init = { servletContext ->

        dogService.save("Spot", 60)
        labradoodleService.save("Chloe", 60)
        humanService.save("Kotlin Ken")

        grailsTeamMemberService.save("Nero")
        grailsTeamMemberService.save("Colin")
        grailsTeamMemberService.save("Graeme")
        grailsTeamMemberService.save("Jack")
        grailsTeamMemberService.save("James")
        grailsTeamMemberService.save("Ryan")
        grailsTeamMemberService.save("Matthew")
        grailsTeamMemberService.save("Will")
        grailsTeamMemberService.save("Alvaro")
        grailsTeamMemberService.save("Dave")
        grailsTeamMemberService.save("Ivan")
        grailsTeamMemberService.save("Jeff")
        grailsTeamMemberService.save("Paul")
        grailsTeamMemberService.save("Ben")
        grailsTeamMemberService.save("Sergio")
        grailsTeamMemberService.save("Zack")
    }
    def destroy = {
    }
}
