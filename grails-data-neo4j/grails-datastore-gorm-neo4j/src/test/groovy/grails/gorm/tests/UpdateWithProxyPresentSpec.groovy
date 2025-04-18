package grails.gorm.tests

import spock.lang.Ignore
import spock.lang.Specification

@Ignore("TODO: Neo4J association proxy support is broken after GPNEO4J-25 changes")
class UpdateWithProxyPresentSpec extends Specification {

    void "Test update entity with association proxies"() {
        expect: 
        1==0
    }

    void "Test update unidirectional oneToMany with proxy"() {
        expect: 
        1==0
    }
}
