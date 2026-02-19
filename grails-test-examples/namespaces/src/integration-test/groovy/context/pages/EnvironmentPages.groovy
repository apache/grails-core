package context.pages

import geb.Page

class TestEnvironmentHomePage extends Page {

    static String pageTitle = 'Welcome to Grails'

    static url = '/myAppTest'
    static at = { title == pageTitle }
}

class DefaultEnvironmentHomePage extends Page {

    static String pageTitle = 'HTTP Status 404 – Not Found'

    static url = '/myApp'
    static at = { title == pageTitle }
}
