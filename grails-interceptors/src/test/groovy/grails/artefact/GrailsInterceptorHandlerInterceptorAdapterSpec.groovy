/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.artefact

import grails.interceptors.Matcher
import grails.util.GrailsWebMockUtil
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationHandler
import io.micrometer.observation.ObservationRegistry
import org.grails.plugins.web.interceptors.GrailsInterceptorHandlerInterceptorAdapter
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.ModelAndView
import spock.lang.Issue
import spock.lang.Specification

/**
 * @author graemerocher
 */
class GrailsInterceptorHandlerInterceptorAdapterSpec extends Specification{

    void cleanup() {
        RequestContextHolder.setRequestAttributes(null)
    }

    void "Test that an interceptor can cancel request processing"() {
        given:"An interceptor"
            def i = new MyInterceptor()
            def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
            adapter.setInterceptors([i] as Interceptor[])


        when:"The adapter prehandle is executed"
            def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        then:"The prehandle is true"
            adapter.preHandle(webRequest.request, webRequest.response, this)

        when:"A condition is met for exclusion"
            webRequest.request.setAttribute("something", "test")
        then:"The prehandle is false"
            !adapter.preHandle(webRequest.request, webRequest.response, this)

    }

    void "Test that an interceptor can cancel view rendering"() {
        given:"An interceptor"
        def i = new MyInterceptor()
        def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
        adapter.setInterceptors([i] as Interceptor[])


        when:"The adapter prehandle is executed"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def modelAndView = new ModelAndView()
        adapter.preHandle(webRequest.request, webRequest.response, this)
        adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)

        then:"The prehandle is true"
            modelAndView.model.foo == 'bar'
            modelAndView.viewName== 'foo'

        when:"A condition is met for exclusion"
        webRequest.request.setAttribute("bar", "test")
        adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)

        then:"The prehandle is false"
        modelAndView.viewName == null
    }

    void "Test the execution order of a repeated postHandle"() {
        given: "a matched pair of interceptors and a completed preHandle"
            def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
            adapter.setInterceptors([new HighestInterceptor(), new LowestInterceptor()] as Interceptor[])
            def webRequest = GrailsWebMockUtil.bindMockWebRequest()
            def modelAndView = new ModelAndView()
            adapter.preHandle(webRequest.request, webRequest.response, this)

        when: "postHandle runs for the dispatch"
            webRequest.request.setAttribute('executed', null)
            adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)

        then: "the after callbacks run in reverse order"
            webRequest.request.getAttribute('executed') == ['lowest after', 'highest after']

        when: "a second dispatch on the same request calls postHandle again"
            webRequest.request.setAttribute('executed', null)
            adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)

        then: "the list held for the request is reversed again"
            webRequest.request.getAttribute('executed') == ['highest after', 'lowest after']
    }

    void "Test an execution order of interceptors"() {
        given: "An interceptor"
            def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
            adapter.setInterceptors([new HighestInterceptor(), new LowestInterceptor()] as Interceptor[])

        when: "The adapter preHandle is executed"
            def webRequest = GrailsWebMockUtil.bindMockWebRequest()
            def modelAndView = new ModelAndView()
            adapter.preHandle(webRequest.request, webRequest.response, this)

        then: "The interceptors are executed in the order of highest priority"
            webRequest.request.getAttribute('executed') == ['highest before', 'lowest before']

        when: "The adapter postHandle is executed"
            webRequest.request.setAttribute('executed', null)
            adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)

        then: "The interceptors are executed in the order of lowest priority"
            webRequest.request.getAttribute('executed') == ['lowest after', 'highest after']

        when: "The adapter afterCompletion is executed"
            webRequest.request.setAttribute('executed', null)
            adapter.afterCompletion(webRequest.request, webRequest.response, this, null)

        then: "The interceptors are executed in the order of lowest priority"
            webRequest.request.getAttribute('executed') == ['lowest afterView', 'highest afterView']
    }

    @Issue('https://github.com/apache/grails-core/issues/9548')
    void "Test the exception is set in the request if thrown"() {
        given:"An interceptor"
        def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
        adapter.setInterceptors([new MyInterceptor()] as Interceptor[])
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        expect:
        !webRequest.request.getAttribute(Matcher.THROWABLE)

        when:
        adapter.afterCompletion(webRequest.request, webRequest.response, this, new Exception("foo"))

        then:
        webRequest.request.getAttribute(Matcher.THROWABLE) instanceof Exception
    }

    void "Test observation is disabled by default and the registry is left untouched"() {
        given: "An adapter with the default observation registry"
        def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
        adapter.setInterceptors([new HighestInterceptor(), new LowestInterceptor()] as Interceptor[])

        expect: "The default registry is a no-op"
        adapter.observationRegistry.isNoop()

        when: "A request is handled"
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def modelAndView = new ModelAndView()
        adapter.preHandle(webRequest.request, webRequest.response, this)

        then: "The interceptors still run in order"
        webRequest.request.getAttribute('executed') == ['highest before', 'lowest before']

        when: "The remaining phases run"
        webRequest.request.setAttribute('executed', null)
        adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)
        adapter.afterCompletion(webRequest.request, webRequest.response, this, null)

        then: "They still run in reverse order"
        webRequest.request.getAttribute('executed') == ['lowest after', 'highest after', 'lowest afterView', 'highest afterView']
    }

    void "Test a no-op registry is never asked to create an observation"() {
        given: "An adapter wired to a registry that reports itself as a no-op"
        def registry = Mock(ObservationRegistry)
        registry.isNoop() >> true
        def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
        adapter.observationRegistry = registry
        adapter.setInterceptors([new MyInterceptor()] as Interceptor[])
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when: "A request is handled"
        def proceed = adapter.preHandle(webRequest.request, webRequest.response, this)
        adapter.postHandle(webRequest.request, webRequest.response, this, new ModelAndView())

        then: "The interceptor result is honoured"
        proceed

        and: "No observation state is created from the registry"
        0 * registry.observationConfig()
        0 * registry.getCurrentObservationScope()
    }

    void "Test a null observation registry falls back to invoking the interceptor directly"() {
        given: "An adapter with no observation registry at all"
        def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
        adapter.observationRegistry = null
        adapter.setInterceptors([new MyInterceptor()] as Interceptor[])
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def modelAndView = new ModelAndView()

        when: "A request is handled"
        def proceed = adapter.preHandle(webRequest.request, webRequest.response, this)
        adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)

        then: "The interceptor callbacks still drive the result"
        proceed
        modelAndView.model.foo == 'bar'
        modelAndView.viewName == 'foo'
    }

    void "Test an observation is recorded for each interceptor phase when observation is enabled"() {
        given: "An adapter wired to a registry with a recording handler"
        def handler = new RecordingObservationHandler()
        def adapter = observingAdapter(handler, new HighestInterceptor(), new LowestInterceptor())
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def modelAndView = new ModelAndView()

        when: "The before phase runs"
        adapter.preHandle(webRequest.request, webRequest.response, this)

        then: "One observation per matched interceptor is recorded, in execution order"
        handler.started.size() == 2
        handler.stopped.size() == 2
        handler.errored.isEmpty()
        handler.scopesOpened == 2
        handler.scopesClosed == 2

        and: "Each observation carries the interceptor name and phase"
        handler.started.every { it.name == 'grails.interceptor' }
        handler.names == ['highest', 'lowest']
        handler.phases == ['before', 'before']
        handler.contextualNames == ['grails.interceptor highest', 'grails.interceptor lowest']

        when: "The after phase runs"
        handler.reset()
        adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)

        then: "Observations are recorded in reverse order and tagged as the after phase"
        handler.started.size() == 2
        handler.names == ['lowest', 'highest']
        handler.phases == ['after', 'after']
        handler.contextualNames == ['grails.interceptor lowest', 'grails.interceptor highest']

        when: "The afterView phase runs"
        handler.reset()
        adapter.afterCompletion(webRequest.request, webRequest.response, this, null)

        then: "It is not observed"
        handler.started.isEmpty()
    }

    void "Test an observed interceptor that vetoes the request still cancels processing"() {
        given: "An observed interceptor that vetoes"
        def handler = new RecordingObservationHandler()
        def adapter = observingAdapter(handler, new MyInterceptor())
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        webRequest.request.setAttribute('something', 'test')

        when: "The before phase runs"
        def proceed = adapter.preHandle(webRequest.request, webRequest.response, this)

        then: "Processing is cancelled and the observation is still completed cleanly"
        !proceed
        handler.started.size() == 1
        handler.stopped.size() == 1
        handler.errored.isEmpty()
        handler.scopesClosed == 1
    }

    void "Test an observed interceptor that vetoes view rendering still clears the model and view"() {
        given: "An observed interceptor that vetoes the after phase"
        def handler = new RecordingObservationHandler()
        def adapter = observingAdapter(handler, new MyInterceptor())
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()
        def modelAndView = new ModelAndView()
        adapter.preHandle(webRequest.request, webRequest.response, this)
        adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)

        when: "A condition is met for exclusion"
        webRequest.request.setAttribute('bar', 'test')
        adapter.postHandle(webRequest.request, webRequest.response, this, modelAndView)

        then: "The view is cleared"
        modelAndView.viewName == null

        and: "Every observation was stopped"
        handler.started.size() == handler.stopped.size()
        handler.errored.isEmpty()
    }

    void "Test an exception thrown by an observed interceptor is recorded and rethrown"() {
        given: "An observed interceptor that throws"
        def handler = new RecordingObservationHandler()
        def failure = new IllegalStateException('boom')
        def adapter = observingAdapter(handler, new ExplodingInterceptor(failure: failure))
        def webRequest = GrailsWebMockUtil.bindMockWebRequest()

        when: "The before phase runs"
        adapter.preHandle(webRequest.request, webRequest.response, this)

        then: "The original exception propagates"
        def e = thrown(IllegalStateException)
        e.is(failure)

        and: "The observation recorded the error and was still stopped"
        handler.errored.size() == 1
        handler.errored.first().error.is(failure)
        handler.stopped.size() == 1
        handler.scopesOpened == 1
        handler.scopesClosed == 1
    }

    void "Test interceptor names are resolved per interceptor class across repeated requests"() {
        given: "An adapter observing two different interceptor classes"
        def handler = new RecordingObservationHandler()
        def adapter = observingAdapter(handler, new HighestInterceptor(), new LowestInterceptor())

        when: "Two requests are handled by the same adapter"
        def firstRequest = GrailsWebMockUtil.bindMockWebRequest()
        adapter.preHandle(firstRequest.request, firstRequest.response, this)
        def firstNames = handler.names

        handler.reset()
        RequestContextHolder.setRequestAttributes(null)
        def secondRequest = GrailsWebMockUtil.bindMockWebRequest()
        adapter.preHandle(secondRequest.request, secondRequest.response, this)

        then: "The cached names are per class, not shared between classes or requests"
        firstNames == ['highest', 'lowest']
        handler.names == ['highest', 'lowest']
    }

    void "Test the matched interceptor list is not carried over between requests"() {
        given: "An adapter reused across requests"
        def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
        adapter.setInterceptors([new HighestInterceptor(), new LowestInterceptor()] as Interceptor[])

        when: "A first request runs through every phase"
        def firstRequest = GrailsWebMockUtil.bindMockWebRequest()
        adapter.preHandle(firstRequest.request, firstRequest.response, this)
        firstRequest.request.setAttribute('executed', null)
        adapter.postHandle(firstRequest.request, firstRequest.response, this, new ModelAndView())
        adapter.afterCompletion(firstRequest.request, firstRequest.response, this, null)

        then: "The order is reversed exactly once"
        firstRequest.request.getAttribute('executed') == ['lowest after', 'highest after', 'lowest afterView', 'highest afterView']

        when: "A second request runs through the same adapter"
        RequestContextHolder.setRequestAttributes(null)
        def secondRequest = GrailsWebMockUtil.bindMockWebRequest()
        adapter.preHandle(secondRequest.request, secondRequest.response, this)

        then: "The before phase still runs in the original order"
        secondRequest.request.getAttribute('executed') == ['highest before', 'lowest before']

        when: "The remaining phases run"
        secondRequest.request.setAttribute('executed', null)
        adapter.postHandle(secondRequest.request, secondRequest.response, this, new ModelAndView())
        adapter.afterCompletion(secondRequest.request, secondRequest.response, this, null)

        then: "The order is reversed again, independently of the first request"
        secondRequest.request.getAttribute('executed') == ['lowest after', 'highest after', 'lowest afterView', 'highest afterView']
    }

    private static GrailsInterceptorHandlerInterceptorAdapter observingAdapter(RecordingObservationHandler handler,
                                                                              Interceptor... interceptors) {
        def registry = ObservationRegistry.create()
        registry.observationConfig().observationHandler(handler)
        def adapter = new GrailsInterceptorHandlerInterceptorAdapter()
        adapter.observationRegistry = registry
        adapter.setInterceptors(interceptors)
        adapter
    }
}
class RecordingObservationHandler implements ObservationHandler<Observation.Context> {

    final List<Observation.Context> started = []
    final List<Observation.Context> stopped = []
    final List<Observation.Context> errored = []
    int scopesOpened
    int scopesClosed

    @Override
    void onStart(Observation.Context context) {
        started << context
    }

    @Override
    void onStop(Observation.Context context) {
        stopped << context
    }

    @Override
    void onError(Observation.Context context) {
        errored << context
    }

    @Override
    void onScopeOpened(Observation.Context context) {
        scopesOpened++
    }

    @Override
    void onScopeClosed(Observation.Context context) {
        scopesClosed++
    }

    @Override
    boolean supportsContext(Observation.Context context) {
        true
    }

    List<String> getNames() {
        started.collect { it.getLowCardinalityKeyValue('grails.interceptor')?.value }
    }

    List<String> getPhases() {
        started.collect { it.getLowCardinalityKeyValue('grails.interceptor.phase')?.value }
    }

    List<String> getContextualNames() {
        started.collect { it.contextualName }
    }

    void reset() {
        started.clear()
        stopped.clear()
        errored.clear()
        scopesOpened = 0
        scopesClosed = 0
    }
}
class ExplodingInterceptor implements Interceptor {

    RuntimeException failure

    ExplodingInterceptor() {
        matchAll()
    }

    @Override
    boolean before() {
        throw failure
    }
}
class MyInterceptor implements Interceptor {

    MyInterceptor() {
        matchAll()
    }

    @Override
    boolean before() {
        if(request.getAttribute("something")) {
            return false
        }
        return true
    }

    @Override
    boolean after() {
        if(request.getAttribute("bar")) {
            return false
        }
        else {
            model = [foo:"bar"]
            view = "foo"
            return true
        }
    }

    @Override
    void afterView() {
       if(request.getAttribute("bar")) {
           throw throwable
       }
    }
}
class HighestInterceptor implements Interceptor {

    int order = HIGHEST_PRECEDENCE

    HighestInterceptor() {
        matchAll()
    }

    @Override
    boolean before() {
        executed << 'highest before'
        true
    }

    @Override
    boolean after() {
        executed << 'highest after'
        true
    }

    @Override
    void afterView() {
        executed << 'highest afterView'
    }

    def getExecuted() {
        def executed = request.getAttribute('executed')
        if (!executed) {
            executed = []
            request.setAttribute('executed', executed)
        }
        executed
    }
}
class LowestInterceptor implements Interceptor {

    int order = LOWEST_PRECEDENCE

    LowestInterceptor() {
        matchAll()
    }

    @Override
    boolean before() {
        executed << 'lowest before'
        true
    }

    @Override
    boolean after() {
        executed << 'lowest after'
        true
    }

    @Override
    void afterView() {
        executed << 'lowest afterView'
    }

    def getExecuted() {
        def executed = request.getAttribute('executed')
        if (!executed) {
            executed = []
            request.setAttribute('executed', executed)
        }
        executed
    }
}
