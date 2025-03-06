package functional.tests

class Embedded {

    String name
    CustomClass customClass
    InSameFile inSameFile

    static constraints = { }

    static embedded = ['customClass', 'inSameFile']
}


class InSameFile {
    String text
}