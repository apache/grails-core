package grails.gorm.tests

/**
 * Abstract base test for loading proxies. Subclasses should do the necessary setup to configure GORM
 */
class ProxyLoadingSpec extends GormDatastoreSpec {

    void "Test load proxied instance directly"() {

        given:
            def t = new TestEntity(name:"Bob", age: 45, child:new ChildEntity(name:"Test Child")).save(flush:true)

        when:
            def proxy = TestEntity.load(t.id)

        then:
            proxy != null
            t.id == proxy.id
            "Bob" == proxy.name
    }

    void "Test query using proxied association"() {
        given:
            def child = new ChildEntity(name: "Test Child")
            def t = new TestEntity(name:"Bob", age: 45, child:child).save()

        when:
            def proxy = ChildEntity.load(child.id)
            t = TestEntity.findByChild(proxy)

        then:
            t != null
    }
}
