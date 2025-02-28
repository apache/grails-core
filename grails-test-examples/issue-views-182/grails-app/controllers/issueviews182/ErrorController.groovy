package issueviews182

class ErrorController {

    def customError () {
        throw new IllegalStateException('Error')
    }
}
