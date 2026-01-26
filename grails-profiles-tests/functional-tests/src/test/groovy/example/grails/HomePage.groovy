package example.grails

import geb.Page

class HomePage extends Page {

    static url = '/#/'

    static at = { title.contains('Grails') }

    static content = {
        controllers(wait: true) { $('#controllers li') }
    }

    List<String> controllerNames() {
        controllers.collect { it.text() } as List<String>
    }
}
