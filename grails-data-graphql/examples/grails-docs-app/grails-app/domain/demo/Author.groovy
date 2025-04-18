package demo

import org.grails.gorm.graphql.entity.dsl.GraphQLMapping

class Author {

    String name

    // tag::locationDefinition[]
    Map homeLocation
    // end::locationDefinition[]

    // tag::associationDefinition[]
    //The key is the ISBN
    Map<String, Book> books

    static hasMany = [books: Book]
    // end::associationDefinition[]

    static constraints = {
    }

    static graphql = GraphQLMapping.build {

        // tag::customBooks[]
        exclude 'books' //<1>

        add('books', 'BookMap') { //<2>
            type { //<3>
                field('key', String)
                field('value', Book)
                collection true
            }
            dataFetcher { Author author ->
                //author.books.entrySet() does not work here because
                //the graphql-java implementation calls .get() on maps
                author.books.collect { key, value -> //<4>
                    [key: key, value: value]
                }.sort(true, {a, b -> a.value.id <=> b.value.id})
            }
        }
        // end::customBooks[]

        // tag::customLocation[]
        exclude 'homeLocation' //<1>

        add('homeLocation', 'Location') { //<2>
            type { //<3>
                field('lat', String)
                field('long', String)
            }
        }
        // end::customLocation[]
    }
}
