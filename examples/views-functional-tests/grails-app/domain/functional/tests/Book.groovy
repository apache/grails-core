package functional.tests

class Book {

    String title
    TimeZone timeZone = TimeZone.getTimeZone("America/New_York")

    static constraints = {
        title blank:false
    }
}
