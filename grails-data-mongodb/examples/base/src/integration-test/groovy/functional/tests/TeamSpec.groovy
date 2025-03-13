package functional.tests

import grails.testing.mixin.integration.Integration

import spock.lang.Specification

@Integration(applicationClass = Application)
class TeamSpec extends Specification {

    void "get() doesn't throw NPE"() {
        when:
        Team team = Team.get("12345")

        then:
        !team
    }

}
