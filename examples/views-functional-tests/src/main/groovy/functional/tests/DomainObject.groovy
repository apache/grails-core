package functional.tests

import groovy.transform.CompileStatic
import groovy.transform.ToString

@CompileStatic
@ToString(includePackage = false, includeNames = true)
class DomainObject {
    String id
    Integer value

    static Map<String, DomainObject> EXAMPLE_MAP() {
        [
                one: new DomainObject(id: 'id', value: 1),
                two: new DomainObject(id: 'id2', value: 2),
        ]
    }
}
