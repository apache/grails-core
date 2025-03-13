package functional.tests

import grails.mongodb.MongoEntity

/**
 * Created by graemerocher on 17/10/16.
 */
class Person implements MongoEntity<Person> {

    String name
    Birthday birthday
}
