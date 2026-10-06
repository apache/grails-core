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
package org.apache.grails.startup

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

import groovy.json.JsonSlurper

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll
import spock.util.concurrent.PollingConditions

import org.springframework.boot.Banner
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.SpringApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory
import org.springframework.boot.web.server.PortInUseException
import org.springframework.boot.web.server.context.WebServerApplicationContext
import org.springframework.boot.web.server.servlet.context.ServletWebServerInitializedEvent
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.ApplicationListener
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import org.springframework.context.annotation.Scope
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.core.env.Environment
import org.springframework.core.metrics.ApplicationStartup
import org.springframework.web.SpringServletContainerInitializer

import grails.boot.StartupTask
import grails.util.Environment as GrailsEnvironment

class StartupProgressSpec extends Specification {

    private static final String STATUS_PATH = '/__grails/startup-progress'

    private static final String HTML = 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'

    /** Stands in for a browser: a program that records the address it is asked to open. */
    private static final String RECORDING_BROWSER = '''
        import java.nio.file.Files;
        import java.nio.file.Path;
        import java.nio.file.StandardOpenOption;

        public class RecordingBrowser {
            public static void main(String[] args) throws Exception {
                Files.writeString(Path.of(args[0]), args[1] + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        }
        '''

    @Shared
    File browserDir

    PollingConditions conditions = new PollingConditions(timeout: 30, delay: 0.05)

    int port

    BufferingApplicationStartup recorder

    Thread runner

    volatile ConfigurableApplicationContext context

    volatile Throwable runFailure

    /** The address the application opened a browser on, which signs a browser in when there are details to see. */
    String signInAddress

    /** The cookie a browser holds once it has signed in. */
    String cookie

    void setupSpec() {
        browserDir = Files.createTempDirectory('startup-progress-browser').toFile()
        new File(browserDir, 'RecordingBrowser.java').text = RECORDING_BROWSER
    }

    void cleanupSpec() {
        browserDir?.deleteDir()
    }

    /** What the application logs, which the test logger writes to standard error. */
    ByteArrayOutputStream log = new ByteArrayOutputStream()

    PrintStream standardError

    void setup() {
        port = freePort()
        Gates.reset(port)
        standardError = System.err
        System.setErr(new PrintStream(new Tee(standardError, log), true, 'UTF-8'))
    }

    void cleanup() {
        Gates.openAll()
        runner?.join(30_000)
        context?.close()
        System.setErr(standardError)
    }

    void 'answers on the application port with startup progress until the web server takes the port over'() {
        given: 'a bean that takes its time to create'
        Gates.beanCreation = new CountDownLatch(1)

        when: 'the application starts, and a browser signs in with the address in the log'
        startSignedIn('grails.startup.progress.enabled': true)

        then: 'a status poll names the bean being created'
        conditions.eventually {
            Map status = status()?.json()
            assert status?.phase == 'CREATING_BEANS'
            assert status.activity == 'slowStartBean'
        }

        when: 'a page polls, a browser navigates, a form posts, and another client calls'
        Response poll = request(STATUS_PATH)
        Map status = poll.json()
        Response page = request('/hello', HTML)
        Response post = request('/form', HTML, 'POST')
        Response head = request('/hello', HTML, 'HEAD')
        Response api = request('/hello', 'application/json')

        then: 'the poll is answered with the phase and how many beans are created'
        poll.status == 200
        poll.phase == 'CREATING_BEANS'
        poll.contentType.startsWith('application/json')
        poll.cacheControl == 'no-store'
        status.runId
        status.beansExpected > 0
        status.beansCreated < status.beansExpected
        status.progress >= 15 && status.progress < 92
        status.showDetails == true
        status.slowestBeans instanceof List

        and: 'the browser gets the progress page, which polls and reloads the page it was asked for'
        page.status == 503
        page.retryAfter
        page.phase == 'CREATING_BEANS'
        page.contentType.startsWith('text/html')
        page.body.contains('<span id="verb">Starting</span> <span id="name">progress-test</span>')
        page.body.contains('"statusPath":"/__grails/startup-progress"')
        page.body.contains('"reload":true')
        page.body.contains("Apache Grails ${GrailsEnvironment.grailsVersion}")

        and: 'a page answering a form post navigates rather than resubmitting the form'
        post.status == 503
        post.body.contains('"reload":false')

        and: 'a HEAD request gets the headers alone'
        head.status == 503
        head.body == ''

        and: 'any other client is told to come back'
        api.status == 503
        api.retryAfter
        api.contentType.startsWith('text/plain')
        api.body == 'progress-test is starting. Try again shortly.\n'

        when: 'the bean is created'
        Gates.beanCreation.countDown()
        runner.join(30_000)

        then: 'the application serves the port'
        runFailure == null
        request('/hello').body == 'hello'
        request('/hello').phase == null

        and: 'a page still polling is told the application is ready, without the poll reaching the application'
        Response readyPoll = request(STATUS_PATH)
        readyPoll.status == 200
        readyPoll.phase == 'READY'
        readyPoll.json() == [phase: 'READY']
        Gates.unmappedRequests == 0

        and: 'the startup recorder the application configured still saw every bean being created'
        recorder.bufferedTimeline.events.any { event ->
            event.startupStep.name == 'spring.beans.instantiate' && event.startupStep.tags.any { tag ->
                tag.key == 'beanName' && tag.value == 'slowStartBean'
            }
        }

        and: 'the application startup is still the kind Spring Boot Actuator serves its startup endpoint for, reporting that recording'
        ApplicationStartup startup = context.getBean(ApplicationStartup)
        startup instanceof BufferingApplicationStartup
        ((BufferingApplicationStartup) startup).bufferedTimeline.events*.startupStep == recorder.bufferedTimeline.events*.startupStep
    }

    void 'keeps reporting progress from the started web server while startup hooks run'() {
        given: 'a bean and a startup hook that both take their time, under a context path'
        Gates.beanCreation = new CountDownLatch(1)
        Gates.startupHook = new CountDownLatch(1)

        when: 'the application starts'
        startSignedIn('grails.startup.progress.enabled': true, 'server.servlet.context-path': '/app')

        then: 'the progress page polls under the context path'
        conditions.eventually {
            assert status('/app')?.phase == 'CREATING_BEANS'
        }
        request('/app/hello', HTML).body.contains('"statusPath":"/app/__grails/startup-progress"')

        when: 'the beans are created and the startup hook is running'
        Gates.beanCreation.countDown()

        then: 'the web server answers the poll with the hook still running'
        conditions.eventually {
            Response poll = status('/app')
            assert poll?.phase == 'INITIALIZING'
            assert poll.json().phase == 'INITIALIZING'
        }

        and: 'a browser opening the address in the log is signed in by the web server too'
        Response signedIn = request("/app/hello?${URI.create(signInAddress).rawQuery}", HTML)
        signedIn.status == 302
        signedIn.location == '/app/hello'
        signedIn.setCookie.startsWith("GRAILS_STARTUP_${port}=")
        status('/app').json().showDetails == true

        and: 'a browser loading a page still gets the progress page'
        Response page = pageLoad('/app/hello')
        page.status == 503
        page.phase == 'INITIALIZING'
        page.contentType.startsWith('text/html')
        page.body.contains('"statusPath":"/app/__grails/startup-progress"')

        and: 'any other request reaches the application, as it did before there was a progress page'
        request('/app/hello', HTML).body == 'hello'
        request('/app/hello').body == 'hello'
        request('/app/hello').phase == null

        when: 'the startup hook finishes'
        Gates.startupHook.countDown()
        runner.join(30_000)

        then: 'the poll is told the application is ready, and a browser loading a page reaches the application'
        runFailure == null
        request("/app${STATUS_PATH}").phase == 'READY'
        pageLoad('/app/hello').body == 'hello'
        Gates.unmappedRequests == 0
    }

    @Unroll
    void 'tells a watching page why the start failed while #stage, then releases the port'() {
        given: 'a start that will fail'
        Gates.beanCreation = new CountDownLatch(1)
        Gates.startupHook = new CountDownLatch(1)
        switch (failIn) {
            case 'bean' -> Gates.beanFailure = 'database is down <b>'
            case 'web server' -> Gates.webServerFailure = 'database is down <b>'
            case 'hook' -> Gates.startupHookFailure = 'database is down <b>'
        }

        when: 'the application starts with a signed-in page watching'
        startSignedIn('grails.startup.progress.enabled': true)
        conditions.eventually {
            assert status()?.phase == 'CREATING_BEANS'
        }
        if (failIn == 'hook') {
            Gates.beanCreation.countDown()
            conditions.eventually {
                assert status()?.phase == 'INITIALIZING'
            }
        }

        and: 'the start fails'
        Gates.openAll()

        then: 'the page is told why before the port is released'
        Response failure = awaitStatus('FAILED')
        Map report = failure.json()
        report.failedPhase == failedPhase
        report.failure.type == failureType
        report.failure.stackTrace.contains('IllegalStateException: database is down <b>')

        and: 'the failure is escaped so the page can never treat it as markup'
        !failure.body.contains('<b>')
        failure.body.contains('\\u003cb\\u003e')

        and: 'another page open on the application, polling on its own schedule, is told as well'
        status()?.phase == 'FAILED'

        when: 'the start finishes failing'
        runner.join(30_000)

        then: 'it failed as it would have without the page, and nothing holds the port'
        rootCause(runFailure) instanceof IllegalStateException
        request('/hello') == null
        portIsFree()

        where:
        stage                     | failIn       | failedPhase           | failureType
        'creating beans'          | 'bean'       | 'CREATING_BEANS'      | 'org.springframework.beans.factory.BeanCreationException'
        'starting the web server' | 'web server' | 'STARTING_WEB_SERVER' | 'org.springframework.context.ApplicationContextException'
        'running a hook'          | 'hook'       | 'INITIALIZING'        | 'java.lang.IllegalStateException'
    }

    void 'leaves internals off the page when details are not shown'() {
        given: 'a start that will fail'
        Gates.beanCreation = new CountDownLatch(1)
        Gates.beanFailure = 'database is down'

        when: 'the application starts without details, asked to open a browser'
        File opened = new File(browserDir, "opened-${port}.txt")
        start(['grails.startup.progress.enabled': true, 'grails.startup.progress.showDetails': false,
               'grails.startup.progress.openBrowser': true] + browser(opened))

        then: 'the status has the phase and counts but not the beans'
        conditions.eventually {
            Map status = status()?.json()
            assert status?.phase == 'CREATING_BEANS'
            assert status.showDetails == false
            assert !status.containsKey('activity')
            assert !status.containsKey('slowestBeans')
            assert !status.containsKey('phases')
            assert !status.containsKey('beansExpected')
        }

        and: 'the page has neither the details nor a way to see them'
        GrailsEnvironment.grailsVersion
        String page = request('/', HTML).body
        !page.contains(GrailsEnvironment.grailsVersion)
        !page.contains('id="phases"')
        !page.contains('id="signin"')

        and: 'with nothing to sign in for, the address opened carries no token'
        awaitOpened(opened) == "http://localhost:${port}/".toString()

        when: 'the start fails'
        Gates.openAll()

        then: 'the page learns that it failed but not why'
        awaitStatus('FAILED').json().failure == [:]
    }

    @Unroll
    void 'does not answer on the port while starting when the page is #setting'() {
        given: 'the default is decided by development mode, which this build is not running in'
        assert !GrailsEnvironment.isDevelopmentMode()
        Gates.beanCreation = new CountDownLatch(1)

        when: 'the application starts and is creating its beans'
        start(properties)
        assert Gates.beanStarted.await(30, TimeUnit.SECONDS)

        then: 'no progress page answers on the port'
        request('/hello', HTML)?.phase == null

        when: 'the beans are created'
        Gates.beanCreation.countDown()
        runner.join(30_000)

        then: 'the application starts as usual'
        runFailure == null
        conditions.eventually {
            assert request('/hello')?.body == 'hello'
        }

        where:
        setting          | properties
        'not configured' | [:]
        'disabled'       | ['grails.startup.progress.enabled': false]
    }

    void 'leaves a port it cannot bind to the web server to report'() {
        given: 'something else holds the port'
        ServerSocket holder = new ServerSocket(port)

        when: 'the application starts'
        start('grails.startup.progress.enabled': true)
        runner.join(30_000)

        then: 'the web server reports the port in use, as it would without the page'
        causes(runFailure).any { it instanceof PortInUseException }

        cleanup:
        holder.close()
    }

    void 'an application deployed to a servlet container does not open the port it is configured with'() {
        given: 'a servlet container deploying an application whose server.port is a port of its own'
        Gates.beanCreation = new CountDownLatch(1)
        int configuredPort = freePort()
        Gates.port = configuredPort
        File baseDir = Files.createTempDirectory('startup-progress').toFile()
        Tomcat container = new Tomcat(port: port, baseDir: baseDir.absolutePath)
        container.connector
        Context deployment = container.addContext('', baseDir.absolutePath)
        deployment.addServletContainerInitializer(new SpringServletContainerInitializer(), [DeployedApplication] as Set<Class<?>>)

        when: 'the container starts deploying the application, which is creating its beans'
        runner = Thread.start('startup-progress-container') {
            try {
                container.start()
            }
            catch (Throwable failure) {
                runFailure = failure
            }
        }
        assert Gates.beanStarted.await(30, TimeUnit.SECONDS)

        then: 'no progress page answers on the port the application is configured with'
        request(configuredPort, '/hello', HTML)?.phase == null

        when: 'the beans are created'
        Gates.beanCreation.countDown()
        runner.join(30_000)

        then: 'the container serves the application on its own port'
        runFailure == null
        conditions.eventually {
            assert request('/hello')?.body == 'hello'
        }

        cleanup:
        container.stop()
        container.destroy()
        baseDir.deleteDir()
    }

    void 'opens a browser on the progress page as soon as the page is served, and only once'() {
        given: 'a bean that takes its time to create'
        Gates.beanCreation = new CountDownLatch(1)
        File opened = new File(browserDir, "opened-${port}.txt")

        when: 'the application starts asked to open a browser'
        start(['grails.startup.progress.enabled': true, 'grails.startup.progress.openBrowser': true] + browser(opened))

        then: 'the browser is opened on the progress page while the beans are still being created'
        conditions.eventually {
            assert opened.exists() && opened.readLines() == ["http://localhost:${port}/".toString()]
        }
        status()?.phase == 'CREATING_BEANS'

        when: 'the application finishes starting'
        Gates.beanCreation.countDown()
        runner.join(30_000)
        Thread.sleep(3000)

        then: 'no second browser is opened on the application'
        runFailure == null
        opened.readLines().size() == 1
    }

    void 'opens a browser on the application once it is ready when the progress page is not served'() {
        given: 'a bean that takes its time to create'
        Gates.beanCreation = new CountDownLatch(1)
        File opened = new File(browserDir, "opened-${port}.txt")

        when: 'the application starts without the page but asked to open a browser'
        start(['grails.startup.progress.enabled': false, 'grails.startup.progress.openBrowser': true] + browser(opened))
        assert Gates.beanStarted.await(30, TimeUnit.SECONDS)
        Thread.sleep(3000)

        then: 'no browser is opened while the application starts'
        !opened.exists()

        when: 'the application finishes starting'
        Gates.beanCreation.countDown()
        runner.join(30_000)

        then: 'a browser is opened on the application'
        runFailure == null
        conditions.eventually {
            assert opened.exists() && opened.readLines() == ["http://localhost:${port}/".toString()]
        }
    }

    void 'does not open another browser when the application starts again in the same JVM'() {
        given: 'an application that opened a browser, which signed in, when it started'
        Gates.beanCreation = new CountDownLatch(1)
        File opened = new File(browserDir, "opened-${port}.txt")
        startSignedIn('grails.startup.progress.enabled': true)
        Gates.beanCreation.countDown()
        runner.join(30_000)

        when: 'it is stopped and started again on the same address, as Spring Boot DevTools restarts it'
        context.close()
        Gates.reset(port)
        Gates.beanCreation = new CountDownLatch(1)
        start(['grails.startup.progress.enabled': true, 'grails.startup.progress.showDetails': true,
               'grails.startup.progress.openBrowser': true] + browser(opened))

        then: 'the browser already open stays signed in to follow the restart'
        conditions.eventually {
            assert status()?.json()?.activity == 'slowStartBean'
        }

        when: 'the restarted application finishes starting'
        Gates.beanCreation.countDown()
        runner.join(30_000)
        Thread.sleep(3000)

        then: 'no second browser was opened'
        runFailure == null
        opened.readLines().size() == 1
    }

    void 'shows the details of the start only to a browser signed in with the address in the log'() {
        given: 'a bean that takes its time to create'
        Gates.beanCreation = new CountDownLatch(1)
        File opened = new File(browserDir, "opened-${port}.txt")

        when: 'the application starts with details, asked to open a browser'
        start(['grails.startup.progress.enabled': true, 'grails.startup.progress.showDetails': true,
               'grails.startup.progress.openBrowser': true] + browser(opened))
        String address = awaitOpened(opened)
        conditions.eventually {
            assert status()?.phase == 'CREATING_BEANS'
        }

        then: 'the browser is opened on an address carrying a token'
        address ==~ /http:\/\/localhost:${port}\/\?grailsStartupToken=[A-Za-z0-9_-]{43}/

        and: 'a browser that has not signed in sees only how far the start has got, and is told how to see more'
        Map anonymous = status().json()
        anonymous.phase == 'CREATING_BEANS'
        anonymous.progress >= 15
        anonymous.elapsedMillis > 0
        anonymous.showDetails == false
        anonymous.signInForDetails == true
        !anonymous.containsKey('activity')
        !anonymous.containsKey('phases')
        !anonymous.containsKey('beansCreated')
        String anonymousPage = request('/', HTML).body
        !anonymousPage.contains("Apache Grails ${GrailsEnvironment.grailsVersion}")

        and: 'the parts of the page that would show the details are not in it at all'
        !anonymousPage.contains('id="phases"')
        !anonymousPage.contains('Preparing the application context')
        !anonymousPage.contains('id="slowest"')
        !anonymousPage.contains('id="failure-trace"')
        anonymousPage.contains('id="signin"')

        and: 'a token that is not the one in the log signs nobody in'
        Response guessed = request('/?grailsStartupToken=guessed', HTML)
        guessed.status == 503
        guessed.setCookie == null

        when: 'the browser opens the address in the log'
        Response signedIn = signIn(address)

        then: 'it is signed in with a cookie scripts cannot read, and sent on to the address without the token'
        signedIn.location == '/'
        signedIn.setCookie.startsWith("GRAILS_STARTUP_${port}=")
        signedIn.setCookie.contains('HttpOnly')
        signedIn.setCookie.contains('SameSite=Strict')

        and: 'from then on it sees the details'
        Map details = status().json()
        details.showDetails == true
        details.signInForDetails == false
        details.activity == 'slowStartBean'
        details.phases*.name.contains('CREATING_BEANS')
        String signedInPage = request('/', HTML).body
        signedInPage.contains("Apache Grails ${GrailsEnvironment.grailsVersion}")
        signedInPage.contains('id="phases"')
        signedInPage.contains('id="slowest"')
        signedInPage.contains('id="failure-trace"')
        !signedInPage.contains('id="signin"')

        and: 'signing in from another page keeps that page and its own parameters'
        request("/books?max=5&${URI.create(address).rawQuery}&sort=title", HTML).location == '/books?max=5&sort=title'
    }

    void 'does not open a browser unless asked to'() {
        given:
        File opened = new File(browserDir, "opened-${port}.txt")

        when: 'the application starts with a browser command but not asked to open it'
        start(['grails.startup.progress.enabled': true] + browser(opened))
        runner.join(30_000)
        Thread.sleep(3000)

        then:
        runFailure == null
        !opened.exists()
    }

    void 'serves a report of the start, as a page or as data, for as long as the application runs'() {
        given: 'a bean that takes its time to create'
        Gates.beanCreation = new CountDownLatch(1)

        when: 'the application starts with the report, and a browser signs in with the address in the log'
        startSignedIn('grails.startup.progress.enabled': true, 'grails.startup.progress.endpoint.enabled': true)
        Response starting = request('/__grails/startup', 'application/json')

        then: 'while the application starts, the report has the progress so far'
        starting.status == 200
        starting.json().phase == 'CREATING_BEANS'

        and: 'a request for it that asks for neither data nor a page is told to come back, and a browser gets the progress page'
        request('/__grails/startup', '*/*').status == 503
        request('/__grails/startup', '*/*').contentType.startsWith('text/plain')
        pageLoad('/__grails/startup').status == 503
        pageLoad('/__grails/startup').body.contains('id="verb"')

        when: 'the bean is created, a little while later, and the application finishes starting'
        Thread.sleep(300)
        Gates.beanCreation.countDown()
        runner.join(30_000)
        context.getBean('lateBean')
        Response page = request('/__grails/startup', HTML)
        Response data = request('/__grails/startup', 'application/json')
        Map report = data.json()

        then: 'a browser gets the report page'
        runFailure == null
        page.status == 200
        page.contentType.startsWith('text/html')
        page.body.contains('"report":{')
        page.body.contains("Apache Grails ${GrailsEnvironment.grailsVersion}")

        and: 'a client asking for JSON gets the report as data'
        data.status == 200
        data.contentType.startsWith('application/json')
        report.application == 'progress-test'
        report.grailsVersion == GrailsEnvironment.grailsVersion
        report.phase == 'READY'
        report.phases*.name == ['PREPARING', 'LOADING_DEFINITIONS', 'CREATING_BEANS', 'STARTING_WEB_SERVER', 'INITIALIZING']
        report.phases.every { it.millis >= 0 }
        report.elapsedMillis >= report.phases.sum { it.millis }
        report.beansCreated == report.beansExpected
        report.slowestBeans.find { it.name == 'slowStartBean' }.millis >= 250

        and: 'a bean the application creates once it has started is no part of the report, however slow'
        !report.slowestBeans*.name.contains('lateBean')

        and: 'so does one asking with the format parameter'
        request('/__grails/startup?format=json', HTML).json() == report

        when: 'someone who has not signed in asks for it'
        cookie = null
        Map anonymous = request('/__grails/startup', 'application/json').json()

        then: 'they get how long the start took, but not how it went, in the page or the data'
        !request('/__grails/startup', HTML).body.contains('id="phases"')
        anonymous.phase == 'READY'
        anonymous.elapsedMillis == report.elapsedMillis
        anonymous.signInForDetails == true
        !anonymous.containsKey('phases')
        !anonymous.containsKey('beansCreated')
        !anonymous.containsKey('slowestBeans')
        !anonymous.containsKey('grailsVersion')

        when: 'they open the report with the token from the log'
        Response signedIn = request("/__grails/startup?${URI.create(signInAddress).rawQuery}", HTML)

        then: 'they are signed in and sent on to the report'
        signedIn.status == 302
        signedIn.location == '/__grails/startup'
        signedIn.setCookie.startsWith("GRAILS_STARTUP_${port}=")

        and: 'the status the progress page polled says the application is ready'
        request(STATUS_PATH).json() == [phase: 'READY']
    }

    void 'serves the report at the path it is given, without the progress page'() {
        when: 'the application starts with the report at a path of its own and no progress page'
        start('grails.startup.progress.enabled': false, 'grails.startup.progress.endpoint.enabled': true,
                'grails.startup.progress.endpoint.path': 'startup-report/')
        runner.join(30_000)

        then: 'the report is served there'
        runFailure == null
        request('/startup-report', 'application/json').json().phase == 'READY'

        and: 'the default path belongs to the application'
        request('/__grails/startup', 'application/json').status == 404
    }

    void 'polls the status at the path it is given'() {
        given: 'a bean and a startup hook that both take their time'
        Gates.beanCreation = new CountDownLatch(1)
        Gates.startupHook = new CountDownLatch(1)

        when: 'the application starts with the status at a path of its own'
        start('grails.startup.progress.enabled': true, 'grails.startup.progress.statusPath': 'ops/startup-status/')

        then: 'the page polls that path, which is answered while the beans are created'
        conditions.eventually {
            assert request('/ops/startup-status')?.phase == 'CREATING_BEANS'
        }
        request('/hello', HTML).body.contains('"statusPath":"/ops/startup-status"')

        when: 'the beans are created and the startup hook is running'
        Gates.beanCreation.countDown()

        then: 'the web server answers the path'
        conditions.eventually {
            assert request('/ops/startup-status')?.phase == 'INITIALIZING'
        }

        when: 'the application finishes starting'
        Gates.startupHook.countDown()
        runner.join(30_000)

        then: 'a poll there is told the application is ready'
        runFailure == null
        request('/ops/startup-status').json() == [phase: 'READY']

        and: 'the default path belongs to the application'
        request(STATUS_PATH).status == 404
    }

    void 'does not serve the report at the path the progress page polls'() {
        when: 'the report is given the path the progress page polls'
        start('grails.startup.progress.enabled': true, 'grails.startup.progress.endpoint.enabled': true,
                'grails.startup.progress.endpoint.path': STATUS_PATH)
        runner.join(30_000)

        then: 'the path is left to tell a polling page the application is ready'
        runFailure == null
        request(STATUS_PATH, HTML).json() == [phase: 'READY']
    }

    void 'shows the tasks the application reports, to a browser signed in to see the details'() {
        given: 'a startup hook that reports a task, one of whose items takes its time'
        Gates.reportTask = true
        Gates.taskItem = new CountDownLatch(1)

        when: 'the application starts with the report, and a browser signs in'
        startSignedIn('grails.startup.progress.enabled': true, 'grails.startup.progress.endpoint.enabled': true)

        then: 'the signed-in page is told what the task does, how far it has got and the item it is on'
        conditions.eventually {
            List tasks = status()?.json()?.tasks
            assert tasks?.size() == 1
            assert tasks[0].description == 'Loading reference data'
            assert tasks[0].completed == 1
            assert tasks[0].total == 3
            assert tasks[0].running == true
            assert tasks[0].item == 'currencies'
        }
        pageLoad('/').body.contains('id="task"')

        and: 'the web server answers a request for the report the way the server before it did'
        request('/__grails/startup', '*/*').status == 503
        request('/__grails/startup', '*/*').contentType.startsWith('text/plain')
        pageLoad('/__grails/startup').status == 503
        pageLoad('/__grails/startup').body.contains('id="verb"')

        when: 'someone who has not signed in looks'
        String signedIn = cookie
        cookie = null
        Map anonymous = status().json()
        String anonymousPage = pageLoad('/').body
        cookie = signedIn

        then: 'the task is neither in the data nor in the page'
        !anonymous.containsKey('tasks')
        !anonymousPage.contains('id="task"')
        !anonymousPage.contains('Loading reference data')

        when: 'the item finishes and the application starts'
        Gates.taskItem.countDown()
        runner.join(30_000)
        Map report = request('/__grails/startup', 'application/json').json()

        then: 'the report has the task, finished, with every item done and how long it took'
        runFailure == null
        report.tasks.size() == 1
        report.tasks[0].description == 'Loading reference data'
        report.tasks[0].completed == 3
        report.tasks[0].total == 3
        report.tasks[0].running == false
        !report.tasks[0].containsKey('item')
        report.tasks[0].millis >= 0

        and: 'the startup recorder the application configured saw the task and its items too'
        recorder.bufferedTimeline.events*.startupStep*.name.count { it == StartupTask.ITEM_STEP } == 3
        recorder.bufferedTimeline.events*.startupStep*.name.contains(StartupTask.TASK_STEP)
    }

    void 'does not serve the report unless it is turned on'() {
        given: 'the default is decided by development mode, which this build is not running in'
        assert !GrailsEnvironment.isDevelopmentMode()

        when: 'the application starts with the progress page but nothing said about the report'
        start('grails.startup.progress.enabled': true)
        runner.join(30_000)

        then: 'the report path belongs to the application'
        runFailure == null
        request('/__grails/startup', 'application/json').status == 404
    }

    void 'a start watched only through the report fails without waiting to tell a page'() {
        given: 'a start that will fail'
        Gates.beanCreation = new CountDownLatch(1)
        Gates.beanFailure = 'database is down'

        when: 'the application starts with the progress page and the report, and only the report is read'
        start('grails.startup.progress.enabled': true, 'grails.startup.progress.endpoint.enabled': true)
        conditions.eventually {
            assert request('/__grails/startup', 'application/json')?.json()?.phase == 'CREATING_BEANS'
        }
        long released = System.nanoTime()
        Gates.openAll()
        runner.join(30_000)
        long failingMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - released)

        then: 'the start fails without holding back for a page that is not there'
        runFailure != null
        failingMillis < 1500
    }

    void 'times the start as Spring Boot does, leaving out the application runners'() {
        given: 'a command line runner that takes its time'
        Gates.runnerMillis = 1500

        when:
        start('grails.startup.progress.enabled': true, 'grails.startup.progress.endpoint.enabled': true)
        runner.join(30_000)
        Map report = request('/__grails/startup', 'application/json').json()

        then: 'the report took as long to start as Spring Boot says it did'
        runFailure == null
        Math.abs(report.elapsedMillis - bootStartedMillis()) < 250
    }

    void 'signs a browser in to the report of an application on a random port under the port it got'() {
        when: 'the application starts on a random port with the report'
        start('grails.startup.progress.enabled': false, 'grails.startup.progress.endpoint.enabled': true,
                'grails.startup.progress.showDetails': true, 'server.port': 0)
        runner.join(30_000)
        port = ((WebServerApplicationContext) context).webServer.port
        URI link = URI.create((log.toString('UTF-8') =~ /Startup report is at (\S+)/)[0][1] as String)
        Response signedIn = request("${link.rawPath}?${link.rawQuery}", HTML)

        then: 'the address logged is on that port, and signing in there names the cookie for it'
        runFailure == null
        link.port == port
        signedIn.status == 302
        signedIn.setCookie.startsWith("GRAILS_STARTUP_${port}=")
    }

    void 'warns when its settings cannot be read, and the application starts without it'() {
        when:
        start('grails.startup.progress.enabled': 'definitely')
        runner.join(30_000)

        then:
        runFailure == null
        log.toString('UTF-8').contains('Not serving startup progress: the grails.startup.progress settings could not be read')
        request('/hello').body == 'hello'
    }

    /** How long Spring Boot logged that the application took to start. */
    private long bootStartedMillis() {
        def started = log.toString('UTF-8') =~ /Started \S+ in ([0-9.]+) seconds/
        assert started.find()
        ((started.group(1) as BigDecimal) * 1000).longValue()
    }

    /**
     * Starts the application with details, asked to open the recording browser, and signs in with the address
     * it was opened on, as a browser does.
     */
    private void startSignedIn(Map<String, Object> properties) {
        File opened = new File(browserDir, "opened-${port}.txt")
        start(['grails.startup.progress.showDetails': true, 'grails.startup.progress.openBrowser': true] + browser(opened) + properties)
        signIn(awaitOpened(opened))
    }

    private Response signIn(String address) {
        signInAddress = address
        URI uri = URI.create(address)
        Response response = request("${uri.rawPath}?${uri.rawQuery}", HTML)
        assert response?.status == 302
        cookie = response.setCookie.split(';')[0]
        response
    }

    private static String awaitOpened(File opened) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline) {
            if (opened.exists() && opened.readLines()) {
                return opened.readLines().first()
            }
            Thread.sleep(50)
        }
        throw new AssertionError("No browser opened within 30 seconds")
    }

    /** The settings that make the recording browser the one that is opened, writing to the given file. */
    private Map<String, Object> browser(File opened) {
        List<String> command = [Path.of(System.getProperty('java.home'), 'bin', 'java').toString(),
                                new File(browserDir, 'RecordingBrowser.java').absolutePath,
                                opened.absolutePath]
        command.withIndex().collectEntries { String argument, int index ->
            ["grails.startup.progress.browserCommand[${index}]".toString(), argument]
        }
    }

    private void start(Map<String, Object> properties) {
        recorder = new BufferingApplicationStartup(10_000)
        SpringApplication application = new SpringApplication(ProgressTestApplication)
        application.registerShutdownHook = false
        application.bannerMode = Banner.Mode.OFF
        application.applicationStartup = recorder
        application.defaultProperties = ['server.port': port, 'spring.application.name': 'progress-test'] + properties
        runner = Thread.start('startup-progress-spec') {
            try {
                context = application.run()
            }
            catch (Throwable failure) {
                runFailure = failure
            }
        }
    }

    private Response awaitStatus(String phase) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (System.nanoTime() < deadline) {
            Response response = status()
            if (response?.phase == phase) {
                return response
            }
            Thread.sleep(50)
        }
        throw new AssertionError("No ${phase} status within 30 seconds")
    }

    private Response status(String contextPath = '') {
        Response response = request("${contextPath}${STATUS_PATH}")
        response?.phase ? response : null
    }

    private Response request(String path, String accept = 'application/json', String method = 'GET') {
        request(port, path, accept, method, cookie)
    }

    /**
     * A page load as a browser makes it, with the fetch metadata browsers send on navigation. It goes
     * through {@link HttpClient} because {@link HttpURLConnection} silently drops {@code Sec-} headers.
     */
    private Response pageLoad(String path) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:${port}${path}"))
                .timeout(Duration.ofSeconds(10))
                .header('Accept', HTML)
                .header('Sec-Fetch-Mode', 'navigate')
                .header('Sec-Fetch-Dest', 'document')
        if (cookie) {
            builder.header('Cookie', cookie)
        }
        HttpRequest request = builder.build()
        try (HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(2)).build()) {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString())
            return new Response(
                    status: response.statusCode(),
                    phase: response.headers().firstValue(StartupProgress.PHASE_HEADER).orElse(null),
                    contentType: response.headers().firstValue('Content-Type').orElse(null),
                    cacheControl: response.headers().firstValue('Cache-Control').orElse(null),
                    retryAfter: response.headers().firstValue('Retry-After').orElse(null),
                    body: response.body())
        }
        catch (IOException ignored) {
            return null
        }
    }

    private static Response request(int port, String path, String accept, String method = 'GET', String cookie = null) {
        HttpURLConnection connection = (HttpURLConnection) URI.create("http://localhost:${port}${path}").toURL().openConnection()
        connection.requestMethod = method
        connection.useCaches = false
        connection.instanceFollowRedirects = false
        if (cookie) {
            connection.setRequestProperty('Cookie', cookie)
        }
        connection.connectTimeout = 2000
        connection.readTimeout = 10_000
        connection.setRequestProperty('Accept', accept)
        try {
            if (method == 'POST') {
                connection.doOutput = true
                connection.outputStream.close()
            }
            int status = connection.responseCode
            InputStream body = status >= 400 ? connection.errorStream : connection.inputStream
            return new Response(
                    status: status,
                    phase: connection.getHeaderField(StartupProgress.PHASE_HEADER),
                    contentType: connection.contentType,
                    cacheControl: connection.getHeaderField('Cache-Control'),
                    retryAfter: connection.getHeaderField('Retry-After'),
                    location: connection.getHeaderField('Location'),
                    setCookie: connection.getHeaderField('Set-Cookie'),
                    body: body?.getText('UTF-8') ?: '')
        }
        catch (IOException ignored) {
            // refused, or closed by a server that is stopping: the progress page treats both as a missed poll
            return null
        }
        finally {
            connection.disconnect()
        }
    }

    private boolean portIsFree() {
        try (ServerSocket socket = new ServerSocket(port)) {
            return socket.localPort == port
        }
        catch (IOException ignored) {
            return false
        }
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.localPort
        }
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> causes = []
        for (Throwable cause = failure; cause != null && !causes.contains(cause); cause = cause.cause) {
            causes << cause
        }
        causes
    }

    private static Throwable rootCause(Throwable failure) {
        causes(failure).last()
    }

    static class Response {

        int status
        String phase
        String contentType
        String cacheControl
        String retryAfter
        String location
        String setCookie
        String body

        Map json() {
            (Map) new JsonSlurper().parseText(body)
        }
    }

    static class Gates {

        static volatile int port
        static volatile CountDownLatch beanStarted
        static volatile CountDownLatch beanCreation
        static volatile CountDownLatch startupHook
        static volatile String beanFailure
        static volatile String startupHookFailure
        static volatile int unmappedRequests
        static volatile boolean reportTask
        static volatile CountDownLatch taskItem
        static volatile String webServerFailure
        static volatile long runnerMillis

        static void reset(int applicationPort) {
            port = applicationPort
            beanStarted = new CountDownLatch(1)
            beanCreation = new CountDownLatch(0)
            startupHook = new CountDownLatch(0)
            beanFailure = null
            startupHookFailure = null
            unmappedRequests = 0
            reportTask = false
            taskItem = new CountDownLatch(0)
            webServerFailure = null
            runnerMillis = 0
        }

        static void openAll() {
            beanCreation.countDown()
            startupHook.countDown()
            taskItem.countDown()
        }

        static void await(CountDownLatch latch) {
            latch.await(30, TimeUnit.SECONDS)
        }
    }

    static class SlowStartBean {
    }

    static class StartupHook implements ApplicationListener<ContextRefreshedEvent> {

        @Override
        void onApplicationEvent(ContextRefreshedEvent event) {
            if (Gates.reportTask) {
                // as an application or plugin reports work of its own, the second item taking its time
                try (StartupTask task = StartupTask.start(event.applicationContext, 'Loading reference data', 3)) {
                    for (String item in ['countries', 'currencies', 'languages']) {
                        task.startItem(item)
                        if (item == 'currencies') {
                            Gates.await(Gates.taskItem)
                        }
                        task.endItem()
                    }
                }
            }
            Gates.await(Gates.startupHook)
            if (Gates.startupHookFailure) {
                throw new IllegalStateException(Gates.startupHookFailure)
            }
        }
    }

    static class HelloServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) {
            response.contentType = 'text/plain'
            response.writer.write('hello')
        }
    }

    /**
     * Stands in for the dispatcher servlet a Grails application maps to {@code /}, which logs a warning for
     * every request it has no mapping for, so it counts the requests for the startup paths that reach it.
     */
    static class NotFoundServlet extends HttpServlet {

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) {
            if (request.requestURI.contains('/__grails/')) {
                Gates.unmappedRequests++
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ApplicationBeans {

        @Bean
        ServletRegistrationBean<HttpServlet> helloServlet() {
            new ServletRegistrationBean<HttpServlet>(new HelloServlet(), '/hello')
        }

        @Bean
        ServletRegistrationBean<HttpServlet> dispatcherServlet() {
            new ServletRegistrationBean<HttpServlet>(new NotFoundServlet(), '/')
        }

        /** Asks for its dependency by an alias, before the bean it is an alias of has been created. */
        @Bean
        @DependsOn('thingAlias')
        DependentThing dependentThing() {
            new DependentThing()
        }

        @Bean(['aliasedThing', 'thingAlias'])
        AliasedThing aliasedThing() {
            new AliasedThing()
        }

        /** Takes its time once its dependency, created on its behalf, has been. */
        @Bean
        SlowStartBean slowStartBean(StartupDependency startupDependency) {
            Gates.beanStarted.countDown()
            Gates.await(Gates.beanCreation)
            if (Gates.beanFailure) {
                throw new IllegalStateException(Gates.beanFailure)
            }
            new SlowStartBean()
        }

        @Bean
        StartupDependency startupDependency() {
            new StartupDependency()
        }

        @Bean
        StartupHook startupHook() {
            new StartupHook()
        }

        @Bean
        WebServerStartedListener webServerStartedListener() {
            new WebServerStartedListener()
        }

        @Bean
        CommandLineRunner slowRunner() {
            { String... args -> Thread.sleep(Gates.runnerMillis) } as CommandLineRunner
        }

        /** Created whenever it is asked for, which the test does once the application has started. */
        @Bean
        @Scope('prototype')
        LateBean lateBean() {
            Thread.sleep(300)
            new LateBean()
        }
    }

    static class StartupDependency {
    }

    static class DependentThing {
    }

    static class AliasedThing {
    }

    static class LateBean {
    }

    static class WebServerStartedListener implements ApplicationListener<ServletWebServerInitializedEvent> {

        @Override
        void onApplicationEvent(ServletWebServerInitializedEvent event) {
            if (Gates.webServerFailure) {
                throw new IllegalStateException(Gates.webServerFailure)
            }
        }
    }

    /** Writes to two streams, so what is written to standard error still reaches it. */
    static class Tee extends OutputStream {

        private final OutputStream first

        private final OutputStream second

        Tee(OutputStream first, OutputStream second) {
            this.first = first
            this.second = second
        }

        @Override
        void write(int b) {
            first.write(b)
            second.write(b)
        }

        @Override
        void write(byte[] bytes, int offset, int length) {
            first.write(bytes, offset, length)
            second.write(bytes, offset, length)
        }

        @Override
        void flush() {
            first.flush()
            second.flush()
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ProgressTestApplication extends ApplicationBeans {

        @Bean
        TomcatServletWebServerFactory webServerFactory(Environment environment) {
            TomcatServletWebServerFactory factory = new TomcatServletWebServerFactory(environment.getRequiredProperty('server.port', Integer))
            factory.contextPath = environment.getProperty('server.servlet.context-path', '')
            factory
        }
    }

    static class DeployedApplication extends SpringBootServletInitializer {

        @Override
        protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
            builder.sources(ApplicationBeans)
                    .bannerMode(Banner.Mode.OFF)
                    .properties('grails.startup.progress.enabled=true', "server.port=${Gates.port}".toString())
        }
    }
}
