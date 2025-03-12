package grails.gorm.tests

/**
 * @author graemerocher
 */
class NegationSpec extends GormDatastoreSpec {

    void "Test negation in dynamic finder"() {
        given:
            new Book(title:"The Stand", author:"Stephen King").save()
            new Book(title:"The Shining", author:"Stephen King").save()
            new Book(title:"Along Came a Spider", author:"James Patterson").save()

        when:
            def results = Book.findAllByAuthorNotEqual("James Patterson")
            def author = Book.findByAuthorNotEqual("Stephen King")

        then:
            results.size() == 2
            results[0].author == "Stephen King"
            results[1].author == "Stephen King"

            author != null
            author.author == "James Patterson"
    }

    void "Test simple negation in criteria"() {
        given:
            new Book(title:"The Stand", author:"Stephen King").save()
            new Book(title:"The Shining", author:"Stephen King").save()
            new Book(title:"Along Came a Spider", author:"James Patterson").save()

        when:
            def results = Book.withCriteria { ne("author", "James Patterson" ) }
            def author = Book.createCriteria().get { ne("author", "Stephen King" ) }

        then:
            results.size() == 2
            results[0].author == "Stephen King"
            results[1].author == "Stephen King"

            author != null
            author.author == "James Patterson"
    }

    void "Test complex negation in criteria"() {
        given:
            new Book(title:"The Stand", author:"Stephen King").save()
            new Book(title:"The Shining", author:"Stephen King").save()
            new Book(title:"Along Came a Spider", author:"James Patterson").save()
            new Book(title:"The Girl with the Dragon Tattoo", author:"Stieg Larsson").save()

        when:
            def results = Book.withCriteria {
                not {
                    eq 'title', 'The Stand'
                    eq 'author', 'James Patterson'
                }
            }

        then:
            results.size() == 2
            results.find { it.author == "Stieg Larsson" } != null
            results.find { it.author == "Stephen King" && it.title == "The Shining" } != null
    }
}
