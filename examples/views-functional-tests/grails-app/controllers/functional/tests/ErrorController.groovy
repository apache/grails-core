package functional.tests

class ErrorController {

    def unauthorized() {
        [ip: request.remoteAddr]
    }
}
