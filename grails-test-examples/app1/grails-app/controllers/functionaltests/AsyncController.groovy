package functionaltests

import static grails.async.web.WebPromises.*

class AsyncController {

    def test() {
        task {
            sleep 500
            render "Hello"
        }
    }
}
