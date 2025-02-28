package grails301.domain.save.npe

class Book {

    String title

    static constraints = {
        title maxSize: 5
    }
}
