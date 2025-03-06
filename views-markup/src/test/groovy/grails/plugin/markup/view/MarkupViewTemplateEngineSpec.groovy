package grails.plugin.markup.view

import grails.web.mapping.LinkGenerator
import spock.lang.Specification

/**
 * Created by graemerocher on 28/08/15.
 */
class MarkupViewTemplateEngineSpec extends Specification {

    void "test links in markup engine"() {
        given:"A template engine"
        def templateEngine = new MarkupViewTemplateEngine()
        def linkGenerator = Mock(LinkGenerator)
        linkGenerator.link(_) >> "http://localhost:8080/book/show/1"
        templateEngine.setLinkGenerator(linkGenerator)

        when:"A template that creates a link is rendered"
        def template = templateEngine.createTemplate('''
model {
    Iterable<Map> cars
}
xmlDeclaration()
cars {
   cars.each {
       car(make: it.make, model: it.model, href:this.g.link(controller:'car'))
   }
}
''')

        def writable = template.make(cars: [[make:"Audi", model:"A5"]])

        def sw = new StringWriter()
        writable.writeTo(sw)

        then:"The result is correct"
        sw.toString().replace('\r','') == '''<?xml version='1.0'?>
<cars>
    <car make='Audi' model='A5' href='http://localhost:8080/book/show/1'/>
</cars>'''
    }

    void "Test parse markup template"() {
        given:"A template engine"
        def templateEngine = new MarkupViewTemplateEngine()

        when:"A template is parsed"
        def template = templateEngine.createTemplate('''
model {
    Iterable<Map> cars
}
xmlDeclaration()
cars {
   cars.each {
       car(make: it.make, model: it.model)
   }
}
''')

        def writable = template.make(cars: [[make:"Audi", model:"A5"]])

        then:"The writable is of the correct type"
        writable instanceof MarkupViewTemplate

        when:"The writable writes"

        def writer = new StringWriter()
        writable.writeTo(writer)

        then:"The output is correct"
        writer.toString().replace('\r','') == '''<?xml version='1.0'?>
<cars>
    <car make='Audi' model='A5'/>
</cars>'''
    }
}
