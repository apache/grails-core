package functionaltests.sitemesh

class LayoutSpecifiedByPropertyController {

    static layout = 'foo'

    def index() {}

    def snippetView() {
        // renders a view that does not contain a root html element
    }
}
