package demo

import grails.converters.JSON
import grails.converters.XML
import grails.web.mime.MimeUtility
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.web.multipart.MultipartFile

class TestController {

    static allowedMethods = [action2: 'POST', action3: ['POST', 'PUT', 'PATCH'], method2: 'POST', method3: ['POST', 'PUT', 'PATCH']]

    MessageSource messageSource
    @Autowired
    MimeUtility mimeUtility

    def action1() {
        render 'action 1'
    }

    def action2() {
        render 'action 2'
    }

    def action3() {
        render 'action 3'
    }

    def method1() {
        render 'method 1'
    }

    def method2() {
        render 'method 2'
    }

    def method3() {
        render 'method 3'
    }

    def handleCommand(TestCommand test) {
        if (test.hasErrors()) {
            render "Bad"
        }
        else {
            render "Good"
        }
    }

    def uploadFile() {
        assert request.method == 'POST'
        assert request.contentType == "multipart/form-data"
        MultipartFile file = request.getFile("myFile")
        file.transferTo(new File("/local/disk/myFile"))
    }

    def renderTemplateContents() {
        def contents = createLink(controller:"foo")
        render contents
    }
    def renderTemplateContentsViaNamespace() {
        def contents = g.render(template:"bar")

        render contents
    }
    def renderText() {
        render "good"
    }

    def redirectToController() {
        redirect(controller:"bar")
    }

    def renderView() {
        render(view:"foo")
    }

    def renderTemplate() {
        render(template:"bar")
    }

    def renderXml() {
        render(contentType:"text/xml") {
            book(title:"Great")
        }
    }

    def renderJson() {
        render(contentType:"text/json") {
            book "Great"
        }
    }

    def renderAsJson() {
        render([foo:"bar"] as JSON)
    }

    def renderWithFormat() {
        def data = [foo:"bar"]
        withFormat {
            xml { render data as XML }
            html data
        }
    }

    def renderWithRequestFormat() {
        def data = [foo:"bar"]
        request.withFormat {
            xml { render data as XML }
            html data
        }
    }

    def renderState() {
        render(contentType:"text/xml") {
            println params.foo
            println request.bar
            requestInfo {
                for (p in params) {
                    parameter(name:p.key, value:p.value)
                }
                request.each {
                    attribute(name:it.key, value:it.value)
                }
            }
        }
    }

    def renderMessage() {
        assert mimeUtility !=null
        assert grailsLinkGenerator != null
        render messageSource.getMessage("foo.bar", null, request.locale)
    }

    def renderWithForm() {
        withForm {
            render "Good"
        }.invalidToken {
            render "Bad"
        }
    }

    // tag::render_attribute[]
    def renderAttribute() {
        render request.getAttribute('foo')
    }
    // end::render_attribute[]

    def fooGet() {
        render 'foo - GET'
    }

    def fooPost() {
        render 'foo - POST'
    }

    def bar() {
        render 'bar'
    }
}

class TestCommand {
    String name

    static constraints = {
        name blank:false
    }
}