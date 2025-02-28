// tag::basic_declaration[]
package demo

class SecondTagLib {
    static defaultEncodeAs = [taglib:'html']

    static namespace = 'two'

    def sayHello = { attrs ->
        out << 'Hello From SecondTagLib'
    }
}
// end::basic_declaration[]
