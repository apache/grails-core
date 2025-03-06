package functional.tests

import grails.gorm.transactions.Transactional

class EmbeddedController {

    static responseFormats = ['json']

    @Transactional
    def index() {
        Embedded embedded = new Embedded(name: "Foo", customClass: new CustomClass(name: "Bar"), inSameFile: new InSameFile(text: "FooBar"))
        embedded.save(flush: true)
        [embedded: embedded]
    }

    @Transactional
    def jsonapi() {
        Embedded embedded = new Embedded(name: "Foo2", customClass: new CustomClass(name: "Bar2"), inSameFile: new InSameFile(text: "FooBar2"))
        embedded.save(flush: true)
        [embedded: embedded]
    }

    @Transactional
    def embeddedWithIncludes() {
        Embedded embedded = new Embedded(name: "Foo3", customClass: new CustomClass(name: "Bar3"), inSameFile: new InSameFile(text: "FooBar3"))
        embedded.save(flush: true)
        [embedded: embedded]
    }

    @Transactional
    def embeddedWithIncludesJsonapi() {
        Embedded embedded = new Embedded(name: "Foo4", customClass: new CustomClass(name: "Bar4"), inSameFile: new InSameFile(text: "FooBar4"))
        embedded.save(flush: true)
        [embedded: embedded]
    }
}
