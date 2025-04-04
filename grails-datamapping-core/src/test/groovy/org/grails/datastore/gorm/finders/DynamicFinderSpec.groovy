package org.grails.datastore.gorm.finders

import spock.lang.Specification

/**
 * Created by graemerocher on 06/02/2017.
 */
class DynamicFinderSpec extends Specification {

    void "test build match spec"() {
        given:
        MatchSpec spec = DynamicFinder.buildMatchSpec(prefix, methodName, parameters)

        expect:
        spec.methodName == methodName
        spec.methodCallExpressions.size() == expressions
        spec.requiredArguments == parameters
        spec.prefix == prefix
        spec.queryExpression == queryExpression

        where:
        prefix   | methodName              | parameters | expressions | queryExpression    |   propertyNames
        "findBy" | "findByTitle"           | 1          |    1        | "Title"            |  ['title']
        "findBy" | "findByTitleBetween"    | 2          |    1        | "TitleBetween"     |  ['title']
        "findBy" | "findByTitleAndAuthor"  | 2          |    2        | "TitleAndAuthor"   |  ['title', 'author']
    }
}
