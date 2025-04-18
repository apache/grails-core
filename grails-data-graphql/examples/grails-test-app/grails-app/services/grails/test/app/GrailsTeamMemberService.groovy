package grails.test.app

import grails.gorm.services.Service
import grails.test.app.inheritance.Dog

@Service(GrailsTeamMember)
interface GrailsTeamMemberService {
    GrailsTeamMember save(String name)
}