package namespaces.admin

class PageController {

    static namespace = "admin"

    def index() {
        render view: "/page/index", model: [pageTitle: "Admin Page"]
    }
}
