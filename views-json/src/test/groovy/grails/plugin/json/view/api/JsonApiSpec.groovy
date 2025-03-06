package grails.plugin.json.view.api

import com.fasterxml.jackson.databind.ObjectMapper
import grails.persistence.Entity
import grails.plugin.json.view.test.JsonRenderResult
import grails.plugin.json.view.test.JsonViewTest
import grails.validation.Validateable
import org.grails.testing.GrailsUnitTest
import spock.lang.Shared
import spock.lang.Specification

class JsonApiSpec extends Specification implements JsonViewTest, GrailsUnitTest {

    @Shared
    ObjectMapper objectMapper = new ObjectMapper()

    void setup() {
        mappingContext.addPersistentEntities(Widget, Author, Book, ResearchPaper)
    }

    void 'test simple case'() {
        given:
        def theWidget = new Widget(name: 'One', width: 4, height: 7)
        theWidget.id = 5

        when:
        def result = render('''
            import grails.plugin.json.view.api.Widget
            model {
                Widget widget
            }
            
            json jsonapi.render(widget)
        ''', [widget: theWidget])

        then:
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "data": {
                    "type": "widget",
                    "id": "5",
                    "attributes": {
                        "width": 4,
                        "height": 7,
                        "name": "One"
                    }
                },
                "links": {
                    "self": "/widget/5"
                }
            }
        ''')
    }

    void 'test Relationships - hasOne'() {
        given:
        Book returnOfTheKing = new Book(
            title: 'The Return of the King',
            author: new Author(name: 'J.R.R. Tolkien')
        )
        returnOfTheKing.id = 3
        returnOfTheKing.author.id = 9

        when:
            def result = render('''
                import grails.plugin.json.view.api.Book
                model {
                    Book book
                }
                
                json jsonapi.render(book)
            ''', [book: returnOfTheKing])

        then: 'The JSON relationships are in place'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "data": {
                    "type": "book",
                    "id": "3",
                    "attributes": {
                        "title": "The Return of the King"
                    },
                    "relationships": {
                        "author": {
                            "links": {
                                "self": "/author/9"
                            },
                            "data": {
                                "type": "author",
                                "id": "9"
                            }
                        }
                    }
                },
                "links": {
                    "self": "/book/3"
                }
            }
        ''')
    }

    void 'test Relationships - multiple'() {
        given:
        ResearchPaper returnOfTheKing = new ResearchPaper(
                title: 'The Return of the King',
                leadAuthor: new Author(name: 'J.R.R. Tolkien'),
                coAuthor: new Author(name: 'Sally Jones'),
                subAuthors: [new Author(name: 'Will'), new Author(name: 'Smith')]
        )
        returnOfTheKing.id = 3
        returnOfTheKing.leadAuthor.id = 9
        returnOfTheKing.coAuthor.id = 10
        returnOfTheKing.subAuthors[0].id = 12
        returnOfTheKing.subAuthors[1].id = 13

        when:
        JsonRenderResult result = render('''
            import grails.plugin.json.view.api.ResearchPaper
            model {
                ResearchPaper researchPaper
            }
            
            json jsonapi.render(researchPaper)
        ''', [researchPaper: returnOfTheKing])

        then: 'The JSON relationships are in place'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "data": {
                    "type": "researchPaper",
                    "id": "3",
                    "attributes": {
                        "title": "The Return of the King"
                    },
                    "relationships": {
                        "subAuthors": {
                            "data": [
                                { "type": "author", "id": "12" },
                                { "type": "author", "id": "13" }
                            ]
                        },
                        "leadAuthor": {
                            "links": {
                                "self": "/author/9"
                            },
                            "data": {
                                "type": "author",
                                "id": "9"
                            }
                        },
                        "coAuthor": {
                            "links": {
                                "self": "/author/10"
                            },
                            "data": {
                                "type": "author",
                                "id": "10"
                            }
                        }
                    }
                },
                "links": {
                    "self": "/researchPaper/3"
                }
            }
        ''')
    }

    void 'test errors'() {
        given:
        def mutepool = new SuperHero()
        mutepool.name = ""
        mutepool.validate()

        when:
        def result = render('''
            import grails.plugin.json.view.api.SuperHero
            model {
                SuperHero hero
            }
            
            json jsonapi.render(hero)
        ''', [hero: mutepool])

        then:
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "errors": [
                    {
                        "code": "blank",
                        "detail": "Property [name] of class [class grails.plugin.json.view.api.SuperHero] cannot be blank",
                        "source": {
                            "object": "grails.plugin.json.view.api.SuperHero",
                            "field": "name",
                            "rejectedValue": "",
                            "bindingError": false
                        }
                    }
                ]
            }
        ''')
    }

    void 'test jsonapi object'() {
        given:
        def theWidget = new Widget(name: 'One', width: 4, height: 7)
        theWidget.id = 5

        when:
        def result = render('''
            import grails.plugin.json.view.api.Widget
            model {
                Widget widget
            }
            
            json jsonapi.render(widget, [jsonApiObject: true])
        ''', [widget: theWidget])

        then:
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "jsonapi": {
                    "version": "1.0"
                },
                "data": {
                    "type": "widget",
                    "id": "5",
                    "attributes": {
                        "width": 4,
                        "height": 7,
                        "name": "One"
                    }
                },
                "links": {
                    "self": "/widget/5"
                }
            }
        ''')
    }

    void 'test compound documents object'() {
        given:
        def returnOfTheKing = new Book(
            title: 'The Return of the King',
            author: new Author(name: 'J.R.R. Tolkien')
        )
        returnOfTheKing.id = 3
        returnOfTheKing.author.id = 9

        when:
        def result = render('''
            import grails.plugin.json.view.api.Book
            model {
                Book book
            }
            
            json jsonapi.render(book, [expand: 'author'])
        ''', [book: returnOfTheKing])

        then: 'The JSON relationships are in place'
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "data": {
                    "type": "book",
                    "id": "3",
                    "attributes": {
                        "title": "The Return of the King"
                    },
                    "relationships": {
                        "author": {
                            "links": {
                                "self": "/author/9"
                            },
                            "data": {
                                "type": "author",
                                "id": "9"
                            }
                        }
                    }
                },
                "links": {
                    "self": "/book/3"
                },
                "included": [
                    {
                        "type": "author",
                        "id": "9",
                        "attributes": {
                            "name": "J.R.R. Tolkien"
                        },
                        "links": {
                            "self": "/author/9"
                        }
                    }
                ]
            }
        ''')
    }

    void 'test meta object rendering with jsonApiObject'() {
        given:
        def theWidget = new Widget(name: 'One', width: 4, height: 7)
        theWidget.id = 5
        def meta = [
                copyright: 'Copyright 2015 Example Corp.',
                authors: [
                        'Yehuda Katz',
                        'Steve Klabnik'
                ]
        ]

        when:
        def result = render('''
            import grails.plugin.json.view.api.Widget
            model {
                Widget widget
                Object meta
            }
            
            json jsonapi.render(widget, [jsonApiObject: true, meta: meta])
        ''', [widget: theWidget, meta: meta])

        then:
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "jsonapi": {
                    "version": "1.0",
                    "meta": {
                        "copyright": "Copyright 2015 Example Corp.",
                        "authors": [
                            "Yehuda Katz",
                            "Steve Klabnik"
                        ]
                    }
                },
                "data": {
                    "type": "widget",
                    "id": "5",
                    "attributes": {
                        "width": 4,
                        "height": 7,
                        "name": "One"
                    }
                },
                "links": {
                    "self": "/widget/5"
                }
            }
        ''')
    }

    void 'test meta object rendering without jsonApiObject'() {
        given:
        def theWidget = new Widget(name: 'One', width: 4, height: 7)
        theWidget.id = 5
        def meta = [
                copyright: 'Copyright 2015 Example Corp.',
                authors: [
                        'Yehuda Katz',
                        'Steve Klabnik'
                ]
        ]

        when:
        def result = render('''
            import grails.plugin.json.view.api.Widget
            model {
                Widget widget
                Object meta
            }
            
            json jsonapi.render(widget, [meta: meta])
        ''', [widget: theWidget, meta: meta])

        then:
        objectMapper.readTree(result.jsonText) == objectMapper.readTree('''
            {
                "meta": {
                    "copyright": "Copyright 2015 Example Corp.",
                    "authors": [
                        "Yehuda Katz",
                        "Steve Klabnik"
                    ]
                },
                "data": {
                    "type": "widget",
                    "id": "5",
                    "attributes": {
                        "width": 4,
                        "height": 7,
                        "name": "One"
                    }
                },
                "links": {
                    "self": "/widget/5"
                }
            }
        ''')
    }
}

@Entity
class Widget {
    String name
    int width
    int height
}

@Entity
class Book {
    String title
    Author author
}

@Entity
class ResearchPaper {
    String title

    @SuppressWarnings('unused')
    static hasMany = [subAuthors: Author]
    Author leadAuthor
    Author coAuthor
}

@Entity
class Author {
    String name
}

class SuperHero implements Validateable {
    String name

    @SuppressWarnings('unused')
    static constraints = {
        name(blank: false)
    }
}