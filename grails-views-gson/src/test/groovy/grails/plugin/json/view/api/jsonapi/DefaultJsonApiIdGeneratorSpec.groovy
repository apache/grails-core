package grails.plugin.json.view.api.jsonapi

import org.grails.datastore.mapping.model.PersistentProperty
import spock.lang.Specification
import spock.lang.Unroll

class DefaultJsonApiIdGeneratorSpec extends Specification {

    @Unroll("DefaultJsonApiIdGenerator.generateId(#subject) == #expectedResult")
    void "test basic ID generation"() {
        given:
            DefaultJsonApiIdRenderer renderer = new DefaultJsonApiIdRenderer()

        when:
            String result = renderer.render(subject, Mock(PersistentProperty) { getName() >> "id "})

        then:
            notThrown(Exception)
            result == expectedResult

        where:
            subject      | expectedResult
            null         | null
            [foo: 'bar'] | null
    }

}
