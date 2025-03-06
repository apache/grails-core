package functional.tests

import grails.gorm.transactions.Transactional

class BulletinController {

    @Transactional
    def index() {
        User newUser = new  User(username: 'user1', publicId: 'user1').save()
        User newUser2 = new User(username: 'user2', publicId: 'user2').save()
        User newUser3 = new User(username: 'user3', publicId: 'user3').save()

        Bulletin bulletin = new Bulletin(name: 'The bulletin', content: 'Hi everyone!')
        bulletin.addToContactUsers(newUser)
        bulletin.addToContactUsers(newUser2)
        bulletin.addToContactUsers(newUser3)
        bulletin.addToTargetUsers(newUser)
        bulletin.addToTargetUsers(newUser2)
        bulletin.save(flush: true)

        respond(bulletin: bulletin)
    }
}
