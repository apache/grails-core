package grails.plugin.json.view.test

import functional.tests.Player
import grails.views.json.test.JsonViewUnitTest
import spock.lang.Specification

/**
 * Created by graemerocher on 14/10/16.
 */
class JsonViewUnitTestSpec extends Specification implements JsonViewUnitTest {

    void "test render json view"() {
        when:"a view is rendered"
        def result = render(template: '/player/player', model: [player: new Player(name: "Cantona")])

        then:"the result is correct"
        result.json.name == "Cantona"
        result.json.sport == 'football'
    }
}
