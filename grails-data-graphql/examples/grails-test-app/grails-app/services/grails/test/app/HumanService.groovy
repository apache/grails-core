package grails.test.app

import grails.gorm.services.Service
import grails.test.app.inheritance.Human

@Service(Human)
interface HumanService {
    Human save(String name)
}