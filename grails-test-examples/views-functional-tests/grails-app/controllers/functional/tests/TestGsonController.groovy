package functional.tests

import grails.plugin.json.view.JsonViewTemplateEngine
import groovy.text.Template
import org.springframework.beans.factory.annotation.Autowired

class TestGsonController {

    @Autowired
    JsonViewTemplateEngine templateEngine

    def testTemplateEngine() {
        Template t = templateEngine.resolveTemplate('/book/show')
        def writable = t.make(book: new Book(title:"The Stand"))
        def sw = new StringWriter()
        writable.writeTo( sw )

        render sw
    }

    def testRespond() {
        def test = new Test(name:"Bob")
        respond test
    }

    def testRespondWithTemplateForDomain() {
        def test = new Test(name:"Bob")
        respond test
    }

    def testCompilationError() {
        [one:"two"]
    }

    def testRuntimeError() {
        [one:"two"]
    }

    def testTemplate() {
        [test: new Test(name:"Bob"), child: new Test(name:"Joe")]
    }

    def testGsonFromPlugin() {
        render view:"/fromPlugin"
    }

    def testInheritsFromPlugin() {
        [:]
    }

    def testRespondWithMap() {
        respond one:'two'
    }

    def testRespondWithMapObjectTemplate() {
        respond one:'two'
    }

    def testLinks() {

    }

    def testAugmentModel() {
        respond new Test(name: "John"), model: [age: 20]
    }
}
