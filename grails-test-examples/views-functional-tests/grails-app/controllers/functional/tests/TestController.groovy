package functional.tests

class TestController {

    def triggerUnauthorized() {
        render status: 401
    }
}
