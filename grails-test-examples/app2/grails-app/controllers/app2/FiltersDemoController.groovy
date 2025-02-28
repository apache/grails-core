package app2

class FiltersDemoController {

    def index() {
        render "Filter Ran: ${params.filterRan}."
    }

    def data() {
        [companyName: 'OCI']
    }
}
