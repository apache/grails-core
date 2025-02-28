package functionaltests

class ForwardingController {

    def one() {
        forward action:"next"
    }

    def two() {
        forward controller:"forwarding", action:"next"
    }

    def three() {
        forward controller:"forwarding", action:"next", params:[param1:'test']
    }

    def next() {
        render "<html><body>Forward Destination. Params: ${params.param1 ?: ''}</body></html>"
    }

    def forwardToList() {
        forward action: 'list'
    }

    def list() {
        [people: ['Jeff', 'Jake', 'Zack', 'Betsy']]
    }

    def putMessageInFlash() {
        flash.message = 'some message'
        forward action: 'displayFlash'
    }

    def displayFlash() {
    }
}
