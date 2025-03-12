package grails.gorm.tests

import grails.persistence.Entity

/**
 * @author graemerocher
 */
class FindByMethodSpec extends GormDatastoreSpec {

    void 'Test Using AND Multiple Times In A Dynamic Finder'() {
        given:
            new Person(firstName: 'Jake', lastName: 'Brown', age: 11).save()
            new Person(firstName: 'Zack', lastName: 'Brown', age: 14).save()
            new Person(firstName: 'Jeff', lastName: 'Brown', age: 41).save()
            new Person(firstName: 'Zack', lastName: 'Galifianakis', age: 41).save()

        when:
            def people = Person.findAllByFirstNameAndLastNameAndAge('Jeff', 'Brown', 1)

        then:
            0 == people?.size()

        when:
            people = Person.findAllByFirstNameAndLastNameAndAgeGreaterThan('Zack', 'Brown', 20)

        then:
            0 == people?.size()

        when:
            people = Person.findAllByFirstNameAndLastNameAndAgeGreaterThan('Zack', 'Brown', 8)

        then:
            1 == people?.size()
            14 == people[0].age

        when:
            def cnt = Person.countByFirstNameAndLastNameAndAge('Jake', 'Brown', 11)

        then:
            1 == cnt

        when:
            cnt = Person.countByFirstNameAndLastNameAndAgeInList('Zack', 'Brown', [12, 13, 14, 15])

        then:
            1 == cnt
    }

    void 'Test Using OR Multiple Times In A Dynamic Finder'() {
        given:
            new Person(firstName: 'Jake', lastName: 'Brown', age: 11).save()
            new Person(firstName: 'Zack', lastName: 'Brown', age: 14).save()
            new Person(firstName: 'Jeff', lastName: 'Brown', age: 41).save()
            new Person(firstName: 'Zack', lastName: 'Galifianakis', age: 41).save()

        when:
            def people = Person.findAllByFirstNameOrLastNameOrAge('Zack', 'Tyler', 125)

        then:
            2 == people?.size()

        when:
            people = Person.findAllByFirstNameOrLastNameOrAge('Zack', 'Brown', 125)

        then:
            4 == people?.size()

        when:
            def cnt = Person.countByFirstNameOrLastNameOrAgeInList('Jeff', 'Wilson', [11, 41])

        then:
            3 == cnt
    }

    void testBooleanPropertyQuery() {
        given:
            new Highway(bypassed: true, name: 'Bypassed Highway').save()
            new Highway(bypassed: true, name: 'Bypassed Highway').save()
            new Highway(bypassed: false, name: 'Not Bypassed Highway').save()
            new Highway(bypassed: false, name: 'Not Bypassed Highway').save()

        when:
            def highways= Highway.findAllBypassedByName('Not Bypassed Highway')

        then:
            0 == highways.size()

        when:
            highways = Highway.findAllNotBypassedByName('Not Bypassed Highway')

        then:
            2 == highways?.size()
            'Not Bypassed Highway' == highways[0].name
            'Not Bypassed Highway'== highways[1].name

        when:
            highways = Highway.findAllBypassedByName('Bypassed Highway')

        then:
            2 == highways?.size()
            'Bypassed Highway'== highways[0].name
            'Bypassed Highway'== highways[1].name

        when:
            highways = Highway.findAllNotBypassedByName('Bypassed Highway')
        then:
            0 == highways?.size()

        when:
            highways = Highway.findAllBypassed()
        then:
            2 ==highways?.size()
            'Bypassed Highway'== highways[0].name
            'Bypassed Highway'==highways[1].name

        when:
            highways = Highway.findAllNotBypassed()
        then:
            2 == highways?.size()
            'Not Bypassed Highway' == highways[0].name
            'Not Bypassed Highway'== highways[1].name

        when:
            def highway = Highway.findNotBypassed()
        then:
            'Not Bypassed Highway' == highway?.name

        when:
            highway = Highway.findBypassed()
        then:
            'Bypassed Highway' == highway?.name

        when:
            highway = Highway.findNotBypassedByName('Not Bypassed Highway')
        then:
            'Not Bypassed Highway' == highway?.name

        when:
            highway = Highway.findBypassedByName('Bypassed Highway')
        then:
            'Bypassed Highway' == highway?.name

        when:
            Book.newInstance(author: 'Jeff', title: 'Fly Fishing For Everyone', published: false).save()
            Book.newInstance(author: 'Jeff', title: 'DGGv2', published: true).save()
            Book.newInstance(author: 'Graeme', title: 'DGGv2', published: true).save()
            Book.newInstance(author: 'Dierk', title: 'GINA', published: true).save()

            def book = Book.findPublishedByAuthor('Jeff')
        then:
            'Jeff' == book.author
            'DGGv2'== book.title

        when:
            book = Book.findPublishedByAuthor('Graeme')
        then:
            'Graeme' == book.author
            'DGGv2'==  book.title

        when:
            book = Book.findPublishedByTitleAndAuthor('DGGv2', 'Jeff')
        then:
            'Jeff'== book.author
            'DGGv2'== book.title

        when:
            book = Book.findNotPublishedByAuthor('Jeff')
        then:
            'Fly Fishing For Everyone'== book.title

        when:
            book = Book.findPublishedByTitleOrAuthor('Fly Fishing For Everyone', 'Dierk')
        then:
            'GINA'== book.title
            Book.findPublished() != null

        when:
            book = Book.findNotPublished()
        then:
            'Fly Fishing For Everyone' == book?.title

        when:
            def books = Book.findAllPublishedByTitle('DGGv2')
        then:
            2 == books?.size()

        when:
            books = Book.findAllPublished()
        then:
            3 == books?.size()

        when:
            books = Book.findAllNotPublished()
        then:
            1 == books?.size()

        when:
            books = Book.findAllPublishedByTitleAndAuthor('DGGv2', 'Graeme')
        then:
            1 == books?.size()

        when:
            books = Book.findAllPublishedByAuthorOrTitle('Graeme', 'GINA')
        then:
            2 == books?.size()

        when:
            books = Book.findAllNotPublishedByAuthor('Jeff')
        then:
            1 == books?.size()

        when:
            books = Book.findAllNotPublishedByAuthor('Graeme')
        then:
            0 == books?.size()
    }

    void "Test findOrCreateBy For A Record That Does Not Exist In The Database"() {
        when:
            def book = Book.findOrCreateByAuthor('Someone')

        then:
            'Someone' == book.author
            null == book.title
            null == book.id
    }

    void "Test findOrCreateBy With An AND Clause"() {
        when:
            def book = Book.findOrCreateByAuthorAndTitle('Someone', 'Something')

        then:
            'Someone' == book.author
            'Something' == book.title
            null == book.id
    }

    void "Test findOrCreateBy Throws Exception If An OR Clause Is Used"() {
        when:
            Book.findOrCreateByAuthorOrTitle('Someone', 'Something')

        then:
            thrown(MissingMethodException)
    }

    void "Test findOrSaveBy For A Record That Does Not Exist In The Database"() {
        when:
            def book = Book.findOrSaveByAuthorAndTitle('Some New Author', 'Some New Title')

        then:
            'Some New Author' == book.author
            'Some New Title' == book.title
            book.id != null
    }

    void "Test findOrSaveBy For A Record That Does Exist In The Database"() {

        given:
            def originalId = new Book(author: 'Some Author', title: 'Some Title').save().id

        when:
            def book = Book.findOrSaveByAuthor('Some Author')

        then:
            'Some Author' == book.author
            'Some Title' == book.title
            originalId == book.id
    }

    void "Test patterns which shold throw MissingMethodException"() {
            // Redis doesn't like Like queries...
//        when:
//            Book.findOrCreateByAuthorLike('B%')
//
//        then:
//            thrown MissingMethodException

        when:
            Book.findOrCreateByAuthorInList(['Jeff'])

        then:
            thrown MissingMethodException

        when:
            Book.findOrCreateByAuthorOrTitle('Jim', 'Title')

        then:
            thrown MissingMethodException

        when:
            Book.findOrCreateByAuthorNotEqual('B')

        then:
            thrown MissingMethodException

        when:
            Book.findOrCreateByAuthorGreaterThan('B')

        then:
            thrown MissingMethodException

        when:
            Book.findOrCreateByAuthorLessThan('B')

        then:
            thrown MissingMethodException

        when:
            Book.findOrCreateByAuthorBetween('A', 'B')

        then:
            thrown MissingMethodException

        when:
            Book.findOrCreateByAuthorGreaterThanEquals('B')

        then:
            thrown MissingMethodException

        when:
            Book.findOrCreateByAuthorLessThanEquals('B')

        then:
            thrown MissingMethodException

            // GemFire doesn't like these...
//        when:
//            Book.findOrCreateByAuthorIlike('B%')
//
//        then:
//            thrown MissingMethodException

//        when:
//            Book.findOrCreateByAuthorRlike('B%')
//
//        then:
//            thrown MissingMethodException

//        when:
//            Book.findOrCreateByAuthorIsNull()
//
//        then:
//            thrown MissingMethodException

//        when:
//            Book.findOrCreateByAuthorIsNotNull()
//
//        then:
//            thrown MissingMethodException

            // Redis doesn't like Like queries...
//        when:
//            Book.findOrSaveByAuthorLike('B%')
//
//        then:
//            thrown MissingMethodException

        when:
            Book.findOrSaveByAuthorInList(['Jeff'])

        then:
            thrown MissingMethodException

        when:
            Book.findOrSaveByAuthorOrTitle('Jim', 'Title')

        then:
            thrown MissingMethodException

        when:
            Book.findOrSaveByAuthorNotEqual('B')

        then:
            thrown MissingMethodException

        when:
            Book.findOrSaveByAuthorGreaterThan('B')

        then:
            thrown MissingMethodException

        when:
            Book.findOrSaveByAuthorLessThan('B')

        then:
            thrown MissingMethodException

        when:
            Book.findOrSaveByAuthorBetween('A', 'B')

        then:
            thrown MissingMethodException

        when:
            Book.findOrSaveByAuthorGreaterThanEquals('B')

        then:
            thrown MissingMethodException

        when:
            Book.findOrSaveByAuthorLessThanEquals('B')

        then:
            thrown MissingMethodException

            // GemFire doesn't like these...
//        when:
//            Book.findOrSaveByAuthorIlike('B%')
//
//        then:
//            thrown MissingMethodException

//        when:
//            Book.findOrSaveByAuthorRlike('B%')
//
//        then:
//            thrown MissingMethodException

//        when:
//            Book.findOrSaveByAuthorIsNull()
//
//        then:
//            thrown MissingMethodException

//        when:
//            Book.findOrSaveByAuthorIsNotNull()
//
//        then:
//            thrown MissingMethodException
    }
}

@Entity
class Highway implements Serializable {
    Long id
    Long version
    Boolean bypassed
    String name

    static mapping = {
        bypassed index:true
        name index:true
    }
}

@Entity
class Book implements Serializable {
    Long id
    Long version
    String author
    String title
    Boolean published = false

    static mapping = {
        published index:true
        title index:true
        author index:true
    }
}
