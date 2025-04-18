package grails.test.app

import grails.gorm.services.Service
import grails.test.app.inheritance.Labradoodle

@Service(Labradoodle)
interface LabradoodleService {
    Labradoodle save(String name, int moveSpeed)
}