package grails.async.services

import grails.async.Promises
import grails.async.web.WebPromises
import grails.util.GrailsWebMockUtil
import org.springframework.web.context.request.RequestContextHolder
import spock.lang.Specification

/**
 * Created by graemerocher on 20/02/2017.
 */
class WebPromisesSpec extends Specification {

    void 'Test web promises handling'() {

        setup:
            GrailsWebMockUtil.bindMockWebRequest()

        when: 'A promise is created'
            def webPromise = WebPromises.task {
                RequestContextHolder.currentRequestAttributes()
            }
            webPromise.get() != null

        then: 'Async was requested'
            def e = thrown(IllegalStateException)
            e.message == 'The current request does not support Async processing'

        when: 'A normal promise is used'
            def promise = Promises.task { 'good' }

        then: 'No request is bound'
            promise.get() == 'good'

        cleanup:
            RequestContextHolder.setRequestAttributes(null)
    }
}
