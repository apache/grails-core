package org.grails.datastore.gorm.validation.constraints.eval

import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.springframework.context.support.StaticMessageSource
import spock.lang.Specification

class DefaultConstraintEvaluatorSpec extends Specification {

    void "test importFrom honors default nullable"() {
        given:
        def evaluator = new DefaultConstraintEvaluator(new DefaultConstraintRegistry(new StaticMessageSource()), new KeyValueMappingContext("test"), Collections.emptyMap())

        when:
        def constraints = evaluator.evaluate(Constraints, true)

        then:
        constraints.name.isNullable()
        !constraints.name.isBlank()

        when:
        constraints = evaluator.evaluate(MoreConstraints, true)

        then:
        constraints.name.isNullable()
        !constraints.name.isBlank()

        when:
        constraints = evaluator.evaluate(MoreConstraints, false)

        then:
        !constraints.name.isNullable()
        !constraints.name.isBlank()
    }

    class Constraints {
        String name
        static constraints = {
            name blank: false
        }
    }

    class MoreConstraints {
        String name
        static constraints = {
            importFrom Constraints
        }
    }
}
