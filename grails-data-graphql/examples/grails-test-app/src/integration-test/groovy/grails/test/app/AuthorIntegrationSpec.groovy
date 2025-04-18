package grails.test.app

import org.grails.gorm.graphql.plugin.testing.GraphQLSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Specification
import spock.lang.Stepwise

@Integration
@Stepwise
class AuthorIntegrationSpec extends Specification implements GraphQLSpec {

    void "test creating an author with multiple books"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              authorCreate(author: {
                name: "Sally",
                books: [
                  {title: "Book 1"},
                  {title: "Book 2"},
                  {title: "Book 3"}
                ]
              }) {
                id
                name
                books {
                  id
                  title
                }
              }
            }
        """)

        def obj = resp.body().data.authorCreate

        then:
        obj.id == 1
        obj.name == "Sally"
        obj.books.size() == 3
        obj.books.find { it.title == "Book 1" } != null
        obj.books.find { it.title == "Book 2" } != null
        obj.books.find { it.title == "Book 3" } != null
    }

    void "test creating an author with a book"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              authorCreate(author: {
                name: "Joseph",
                books: [
                  {title: "XYZ"}
                ]
              }) {
                id
                name
                books {
                  id
                  title
                }
              }
            }
        """)


        def obj = resp.body().data.authorCreate

        then:
        obj.id == 2
        obj.name == "Joseph"
        obj.books.size() == 1
        obj.books[0].id == 4
        obj.books[0].title == "XYZ"
    }

    void "test creating an author with errors"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              authorCreate(author: {
                name: "123456789012345678901"
              }) {
                errors {
                  field
                  message
                }
              }
            }
        """)


        def obj = resp.body().data.authorCreate.errors

        then:
        obj.size() == 1
        obj[0].field == 'name'
        obj[0].message == 'Property [name] of class [class grails.test.app.Author] with value [123456789012345678901] exceeds the maximum size of [20]'
    }

    void "test listing authors"() {
        when:
        def resp = graphQL.graphql("""
            {
              authorList {
                id
                name
                books {
                  id
                  title
                }
              }
            }
        """)
        def json = resp.body()
        println json.toString()
        def authors = json.data.authorList
        def author1 = authors[0]
        def author2 = authors[1]

        then:
        authors.size() == 2
        author1.id == 1
        author1.name == "Sally"
        author1.books.size() == 3
        author1.books.find { it.title == "Book 1" } != null
        author1.books.find { it.title == "Book 2" } != null
        author1.books.find { it.title == "Book 3" } != null
        author2.id == 2
        author2.name == "Joseph"
        author2.books.size() == 1
        author2.books[0].id == 4
        author2.books[0].title == "XYZ"
    }

    void "test querying a single author"() {
        when:
        def resp = graphQL.graphql("""
            {
              author(id: 2) {
                id
                name
                books {
                  id
                  title
                }
              }
            }
        """)

        def author = resp.body().data.author

        then:
        author.id == 2
        author.name == "Joseph"
        author.books.size() == 1
        author.books[0].id == 4
        author.books[0].title == "XYZ"
    }

    void "test updating a book on an author"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              authorUpdate(id: 1, author: {
                name: "Xavier",
                books: [
                  {id: 1, title: "x"},
                  {id: 2}
                ]
              }) {
                id
                name
                books {
                  id
                  title
                }
              }
            }
        """)

        def obj = resp.body().data.authorUpdate

        then:
        obj.id == 1
        obj.name == "Xavier"
        obj.books.size() == 2
        obj.books.find { it.id == 1 }.title == 'x'
        obj.books.find { it.id == 2 } != null
    }

    void "test deleting an author"() {
        when:
        def resp = graphQL.graphql("""
            mutation {
              authorDelete(id: 2) {
                success
              }
            }
        """)

        then:
        resp.body().data.authorDelete.success == true
    }

    void cleanupSpec() {
        graphQL.graphql("""
            mutation {
              authorDelete(id: 1) {
                success
              }
            }
        """)
    }

}
