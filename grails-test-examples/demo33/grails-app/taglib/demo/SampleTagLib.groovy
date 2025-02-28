// tag::basic_declaration[]
package demo

class SampleTagLib {

    static defaultEncodeAs = [taglib:'html']

    static namespace = 'demo'

    // end::basic_declaration[]
    // tag::hello_world[]
    def helloWorld = { attrs ->
        out << 'Hello, World!'
    }
    // end::hello_world[]
    // tag::say_hello[]
    def sayHello = { attrs ->
        out << "Hello, ${attrs.name}!"
    }
    // end::say_hello[]
    // tag::render_some_number[]
    def renderSomeNumber = { attrs ->
        int number = attrs.int('value', -1)
        out << "The Number Is ${number}"
    }
    // end::render_some_number[]

    def renderMessage = {
        out << message(code: 'some.custom.message', locale: request.locale)
    }
// tag::basic_declaration[]
}
// end::basic_declaration[]
