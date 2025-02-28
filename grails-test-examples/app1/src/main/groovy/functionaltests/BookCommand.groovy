package functionaltests

import grails.validation.Validateable

class BookCommand implements Validateable {

    String title
    Date lastUpdated
    Date dateCreated

    static constraints = {
    }
}
