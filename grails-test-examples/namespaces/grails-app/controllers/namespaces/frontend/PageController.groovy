package namespaces.frontend

class PageController {

    def index() {
        render view: "/page/index", model: [pageTitle: "Frontend Page"]
    }
}
