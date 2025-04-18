package grails.test.app

import grails.gorm.services.Service
import grails.test.app.inheritance.Dog

@Service(Dog)
interface DogService {
    Dog save(String name, int moveSpeed)
}