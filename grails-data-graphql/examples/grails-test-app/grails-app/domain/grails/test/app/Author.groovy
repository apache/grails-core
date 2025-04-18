package grails.test.app

class Author {

    String name

    static hasMany = [books: Book]

    static constraints = {
        name maxSize: 20
    }

    static mapping = {
        books cascade: 'all-delete-orphan'
    }

    static graphql = true
}
