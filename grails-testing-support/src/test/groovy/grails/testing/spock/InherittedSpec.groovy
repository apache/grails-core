package grails.testing.spock

import spock.lang.Specification

class InherittedSpec extends ParentSpec {
    void setup() {
        System.out.println("child")
    }

    def "test"() {
        expect:
        true
    }
}

abstract class ParentSpec extends GrandParentSpec {
    void setup() {
        System.out.println("parent")
    }
}

abstract class GrandParentSpec extends Specification implements SomeTrait {
    void setup() {
        System.out.println("grand parent")
    }
}

trait SomeTrait {

}