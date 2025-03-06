package grails.plugin.json.view

import grails.rest.Linkable
import grails.views.ViewCompilationException
import grails.views.api.GrailsView
import grails.web.mapping.LinkGenerator
import groovy.json.JsonSlurper
import spock.lang.Specification

/**
 * Created by graemerocher on 21/08/15.
 */
class JsonViewTemplateEngineSpec extends Specification {
    void "Test model with default value"() {
        when: "An engine is created and a template parsed"
        def templateEngine = new JsonViewTemplateEngine()
        def template = templateEngine.createTemplate('''
model {
    String foo = "bar"
}

json {
    name foo
}
''')
        def writer = new StringWriter()
        template.make().writeTo(writer)
        then:"The output is correct"
        writer.toString() == '{"name":"bar"}'
    }

    void "Test static compilation with collections"() {
        when: "An engine is created and a template parsed"
        def templateEngine = new JsonViewTemplateEngine()
        def template = templateEngine.createTemplate('''
model {
    List<URL> urls
}

json urls, { URL url ->
    protocol url.protocol
}
''')
        def writer = new StringWriter()
        template.make(urls: [new URL("http://foo.com")]).writeTo(writer)
        then:"The output is correct"
        writer.toString() == '[{"protocol":"http"}]'
    }

    void "Test render with includes"() {
        when: "An engine is created and a template parsed"
        def templateEngine = new JsonViewTemplateEngine()
        def template = templateEngine.createTemplate('''
import grails.plugin.json.view.*
model {
    Book book
}

json g.render(book, [includes:['title']])
''')
        def writer = new StringWriter()
        template.make(book: new Book(title:"The Stand")).writeTo(writer)
        then:"The output is correct"
        writer.toString() == '{"title":"The Stand"}'
    }

    void "Test HAL JSON view template"() {
        when:"An engine is created and a template parsed"

        def templateEngine = new JsonViewTemplateEngine(new JsonViewConfiguration(prettyPrint: true), Thread.currentThread().contextClassLoader)
        def linkGenerator = Mock(LinkGenerator)
        linkGenerator.link(_) >> "http://localhost:8080/book/show/1"
        templateEngine.setLinkGenerator(linkGenerator)
        def template = templateEngine.createTemplate('''
import grails.plugin.json.view.*
model {
    Book book
}
hal.type "application/hal+json"
json {
    hal.links(book)
    hal.embedded {
//        authors( book.authors ) { Author author ->
//            name author.name
//        }
        primaryAuthor( book.authors.first() ) { Author author ->
            name author.name
        }
    }
    title book.title
}
''')


        def writer = new StringWriter()


        GrailsView view = (GrailsView)template.make(book: new Book(title:"The Stand", authors: [new Author(name:"Stephen King")] as Set))
        view.writeTo(writer)

        def output = writer.toString()
        then:"The output is correct"
        new JsonSlurper().parse(output.getBytes("UTF-8"))
        output == '''{
    "_links": {
        "self": {
            "href": "http://localhost:8080/book/show/1",
            "hreflang": "en",
            "type": "application/hal+json"
        }
    },
    "_embedded": {
        "primaryAuthor": {
            "_links": {
                "self": {
                    "href": "http://localhost:8080/book/show/1",
                    "hreflang": "en",
                    "type": "application/hal+json"
                }
            },
            "name": "Stephen King"
        }
    },
    "title": "The Stand"
}'''
    }

    void "Test static compilation"() {
        when:"An engine is created and a template parsed"
        def templateEngine = new JsonViewTemplateEngine()
        def template = templateEngine.createTemplate('''
model {
    URL url
}
json.site {
    protocol url.protocol
}
''')

        def writer = new StringWriter()
        template.make(url: new URL("http://foo.com")).writeTo(writer)

        then:"The output is correct"
        writer.toString() == '{"site":{"protocol":"http"}}'

        when:"A template is compiled with a compilation error"
        template = templateEngine.createTemplate('''
model {
    URL url
}
json.site {
    protocol url.frotocol
}
''')
        writer = new StringWriter()
        template.make(url: new URL("http://foo.com")).writeTo(writer)

        then:"A compilation error is thrown"
        thrown ViewCompilationException
    }

    void "Test parsing a JSON view template"() {
        when:"An engine is created and a template parsed"
        def templateEngine = new JsonViewTemplateEngine()
        def template = templateEngine.createTemplate('''
json.person {
    name "bob"
}
''')

        def writer = new StringWriter()
        template.make().writeTo(writer)

        then:"The output is correct"
        writer.toString() == '{"person":{"name":"bob"}}'
    }

    void "Test pretty print a JSON view template"() {
        when:"An engine is created and a template parsed"
        def templateEngine = new JsonViewTemplateEngine()
        def template = templateEngine.createTemplate('''
json.person {
    name "bob"
}
''')

        def writer = new StringWriter()

        def writable = template.make()
        writable.setPrettyPrint(true)
        writable.writeTo(writer)

        then:"The output is correct"
        writer.toString() == '''{
    "person": {
        "name": "bob"
    }
}'''
    }


    void "Test resolveTemplate method"() {
        when:"A templateEngine is created"
        def templateEngine = new JsonViewTemplateEngine()
        def template = templateEngine.resolveTemplate("/foo.gson")

        then:"The template exists"
        template != null

        when:"The template is written"
        def writer = new StringWriter()
        template.make().writeTo(writer)

        then:"The output is correct"
        writer.toString() == '{"person":{"name":"bob"}}'


    }
}

@Linkable
class Book  {
    String title
    Set<Author> authors
}
class Author {
    String name
}