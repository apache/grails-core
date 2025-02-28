package app2

class TestController {

    def throwGeneralError() {
        throw new IllegalStateException("Something bad")
    }
}
