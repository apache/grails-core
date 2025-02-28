// tag::basic_declaration[]
package demo

class DemoController {

// end::basic_declaration[]
    static allowedMethods = [clearDatabase: 'DELETE']

    // tag::render_hello[]
    def hello() {
        render 'Hello, World!'
    }

    // end::render_hello[]
    def clearDatabase() {
        render 'Success'
    }

    def invokeTagWhichInvokesTag() {
        response.writer << one.sayHello()
    }

    def invokeCoreTagAsMethod() {
        // test invoke core tag
        response.writer << link(controller:'demo',action:'clearDatabase')
    }

    private String privateMethod() {
        'From Private'
    }

    protected String protectedMethod() {
        'From Protected'
    }

// tag::basic_declaration[]
}
// end::basic_declaration[]
