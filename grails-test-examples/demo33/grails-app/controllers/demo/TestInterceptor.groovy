package demo

// tag::declaration[]
class TestInterceptor {

    TestInterceptor() {
        match(controller: "test")
    }

    boolean before() {
        request.setAttribute('foo', 'Foo is Bar')
        true
    }
}
// end::declaration[]
