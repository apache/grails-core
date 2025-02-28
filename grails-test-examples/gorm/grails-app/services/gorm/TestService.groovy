package gorm

import grails.gorm.transactions.Transactional

@Transactional
class TestService {

    def serviceMethod() {

    }

    @Transactional
    boolean testDynamicFinders() {
        assert Book.count() == 1
        assert Book.where {
            title == "The Stand"
        }.count() == 1
        assert Book.where {
            title == 'Something Else'
        }.count() == 0
        assert Book.findByTitle("The Stand")
        assert Book.countByTitle("The Stand") == 1
        assert Book.countByTitle("Something Else") == 0
        assert Book.findAllByTitle("The Stand").size() == 1
        return true
    }
}
