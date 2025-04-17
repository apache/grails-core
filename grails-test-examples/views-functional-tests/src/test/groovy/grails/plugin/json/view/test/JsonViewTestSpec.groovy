package grails.plugin.json.view.test

import functional.tests.Player
import grails.plugin.json.view.test.JsonRenderResult
import grails.plugin.json.view.test.JsonViewTest
import spock.lang.Specification

/**
 * Created by graemerocher on 14/10/16.
 */
class JsonViewTestSpec extends Specification implements JsonViewTest {

    void "test render json view"() {
        when:"a view is rendered"
        def result = render(template: '/player/player', model: [player: new Player(name: "Cantona")])

        then:"the result is correct"
        result.json.name == "Cantona"
        result.json.sport == 'football'
    }
}
