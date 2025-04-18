package grails.gorm.tests

import org.grails.datastore.gorm.neo4j.mapping.reflect.Neo4jNameUtils
import spock.lang.Specification

/**
 * @author Stefan Armbruster
 */
class Neo4jNameUtilsSpec extends Specification {

    def "verify singular detection"() {

        expect:
        Neo4jNameUtils.isSingular(term) == result

        where:
        term       | result
        "word"     | true
        "words"    | false
        "friend"   | true
        "friends"  | false
        "buddy"    | true
        "buddies"  | false
        "child"    | true
        "children" | false
        "person"   | true
        //"people"   | false
    }
}
