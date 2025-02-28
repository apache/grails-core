package functionaltests

class BookHibernateSpec extends grails.test.hibernate.HibernateSpec {
    def setup() {
        new Book(title: 'foo').save()
    }

    void "Test that dynamic finders work"() {
        expect:"The book validates"
        	Book.count() == 1
            !Book.findByTitle('hello')

    }
}
