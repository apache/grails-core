package functional.tests

import groovy.transform.CompileStatic

/**
 * Created by Jim on 8/31/2016.
 */
@CompileStatic
enum MyEnum {

    FOO("Foo"),
    BAR("Bar")

    final String value

    MyEnum(String value) {
        this.value = value
    }

    String toString() {
        value
    }
}