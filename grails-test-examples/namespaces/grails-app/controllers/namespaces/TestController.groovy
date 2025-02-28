package namespaces

class TestController {

    static namespace = "my"

    def implicitView() {
        [foo:"bar"]
    }

    def explicitView() {
        render view:"foo", model:[foo:"bar"]
    }
}
