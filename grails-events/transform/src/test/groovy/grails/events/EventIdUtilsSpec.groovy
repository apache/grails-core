package grails.events

import org.grails.events.EventIdUtils
import spock.lang.Specification
import spock.lang.Unroll

class EventIdUtilsSpec extends Specification {

    @Unroll
    def 'For Method Name ( #methodName ) event Id should be ( #expected )'(String methodName, String expected) {

        expect:
        EventIdUtils.eventIdForMethodName(methodName) == expected

        where:
        methodName   || expected
        'onSum'      || 'sum'
        'sum'        || 'sum'
        'onSaveUser' || 'saveUser'
        'saveUser'   || 'saveUser'
        'SaveUser'   || 'SaveUser'
        'onS'        || 's'
        'on'         || 'on'
        'save user'  || 'save user'
    }

}
