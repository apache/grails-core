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
package org.apache.grails.gradle.plugin.aot

import java.net.CookieHandler
import java.net.CookieManager
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

import org.apache.commons.validator.routines.EmailValidator
import org.apache.commons.validator.routines.RegexValidator
import org.apache.commons.validator.routines.UrlValidator
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.internal.logging.events.LogEvent
import org.gradle.internal.logging.events.OutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.slf4j.OutputEventListenerBackedLoggerContext
import org.gradle.testfixtures.ProjectBuilder
import org.slf4j.LoggerFactory
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Covers the run that records what an image will need.
 *
 * <p>The agent itself is GraalVM's and is not exercised here: what is worth covering is what the
 * task asks the application for, because the metadata is only ever as complete as that.</p>
 */
class TraceNativeMetadataTaskSpec extends Specification {

    /** The validator Grails' {@code url} constraint builds, which accepts any scheme. */
    private static final UrlValidator URL_CONSTRAINT = new UrlValidator((RegexValidator) null,
            UrlValidator.ALLOW_ALL_SCHEMES + UrlValidator.ALLOW_2_SLASHES)

    @TempDir
    File temporaryFolder

    Project project = ProjectBuilder.builder().build()

    private File requestedFile
    private File metadata

    List<String> getRequested() {
        requestedFile?.isFile() ? requestedFile.readLines().findAll { it } : []
    }

    /**
     * A task whose run really serves.
     *
     * <p>Launched through a script standing in for GraalVM's java: the agent is asked for on the
     * command line, and a JDK without one stops at load -- which is the thing the task refuses in
     * advance, and is covered separately below.</p>
     */
    private TraceNativeMetadataTask task(List<String> paths, List<String> forms, String behaviour = 'ok') {
        File application = new File(temporaryFolder, 'application')
        application.mkdirs()
        File archive = new File(application, 'traced.jar')
        archive.text = 'stands in for the archive'
        requestedFile = new File(temporaryFolder, 'requested.txt')
        requestedFile.text = ''
        metadata = new File(temporaryFolder, 'metadata')

        // A java with the agent beside it, which is what the task looks for before it starts.
        File home = new File(temporaryFolder, 'graalvm')
        new File(home, 'bin').mkdirs()
        new File(home, 'lib').mkdirs()
        new File(home, 'lib/libnative-image-agent.dylib').text = 'stands in for the agent'
        File script = new File(home, 'bin/java')
        script.text = """#!/bin/sh
port=""
for arg in "\$@"; do
  case "\$arg" in
    --server.port=*) port="\${arg#--server.port=}" ;;
  esac
done
exec '${new File(System.getProperty('java.home'), 'bin/java').absolutePath}' \\
  -cp '${System.getProperty('java.class.path')}' \\
  ${TracedRunFixture.name} "\$port" '${requestedFile.absolutePath}' ${behaviour}
"""
        script.setExecutable(true)

        TraceNativeMetadataTask task = project.tasks.create("trace${paths.size()}${forms.size()}${behaviour}".replaceAll('-', ''), TraceNativeMetadataTask)
        task.archiveFile.set(archive)
        task.javaExecutable.set(script.absolutePath)
        task.jvmArguments.set([])
        task.paths.set(paths)
        task.forms.set(forms)
        task.port.set(18077)
        task.startTimeoutSeconds.set(60)
        task.outputDirectory.set(metadata)
        task
    }

    void 'every path named is asked for'() {
        given:
            TraceNativeMetadataTask task = task(['/', '/login'], [])

        when:
            task.trace()

        then: 'the agent records what ran, so what is not asked for is not in the image'
            requested.contains('GET /')
            requested.contains('GET /login')
    }

    void 'a form is submitted with the fields it declares'() {
        given:
            TraceNativeMetadataTask task = task([], ['/create'])

        when:
            task.trace()

        then: 'to the action the form names, rather than to the page it was read from'
            String posted = requested.find { it.startsWith('POST /save') }
            posted != null

        and: 'carrying the value a field already had, which is how a security token is carried'
            posted.contains('_csrf=a-token')

        and: 'and what a ticked checkbox sends -- the field whose absence means nothing is converted'
            posted.contains('published=on')

        and: 'every other field the form declares'
            posted.contains('title=')
            posted.contains('secret=')

        and: 'but not the button that submits it'
            !posted.contains('go=')
    }

    void 'a form can be told what to put in it'() {
        given: 'a form that authenticates is only worth submitting with credentials that work'
            TraceNativeMetadataTask task = task([], ['/create?title=a-known-title'])

        when:
            task.trace()

        then:
            String posted = requested.find { it.startsWith('POST /save') }
            posted.contains('title=a-known-title')

        and: 'while the rest of the form is still filled in'
            posted.contains('_csrf=a-token')
            posted.contains('published=on')
    }

    void 'a value for a field the form does not have is not sent'() {
        given:
            TraceNativeMetadataTask task = task([], ['/create?nosuchfield=x'])

        when:
            task.trace()

        then: 'rather than posting something the application will ignore and calling it covered'
            String posted = requested.find { it.startsWith('POST /save') }
            !posted.contains('nosuchfield')
    }

    void 'a field rendered with an empty value is filled in, so that the form can succeed'() {
        given: 'blank fields fail validation, the page answers with the form again, and the save is never recorded'
            TraceNativeMetadataTask task = task([], ['/scaffolded'])

        when:
            task.trace()

        then:
            String submitted = requested.find { String line -> line.startsWith('POST /scaffolded-save') }
            submitted =~ /username=traced-\d+/
            submitted.contains('password=traced-secret')

        and: 'what a field does say is sent, and a hidden field is the page\'s own, whatever it says'
            submitted.contains('nickname=kept')
            submitted.contains('_csrf=token')
            submitted =~ /version=(&|$)/
    }

    void 'a field rendered empty is filled in with something its type accepts'() {
        given: 'a number or a date sent text fails to bind, and the form answering again with a 200 reads as a save'
            TraceNativeMetadataTask task = task([], ['/typed?tinyContact=kept'])
            StringBuilder said = new StringBuilder()
            // A task run outside a build logs through Gradle's own logger context, which no public listener reaches
            OutputEventListenerBackedLoggerContext logging = (OutputEventListenerBackedLoggerContext) LoggerFactory.ILoggerFactory
            OutputEventListener console = logging.outputEventListener
            logging.outputEventListener = { OutputEvent event ->
                if (event instanceof LogEvent && event.logLevel == LogLevel.WARN) {
                    said.append(((LogEvent) event).message).append('\n')
                }
                console.onOutput(event)
            } as OutputEventListener

        when:
            try {
                task.trace()
            }
            finally {
                logging.outputEventListener = console
            }
            String warned = said.toString()

        then:
            Map<String, String> sent = postedTo('/typed-save')
            with(sent) {
                available == 'on'
                (title =~ /^traced-\d+$/) && title.size() <= 12
                (notes =~ /^\d+$/) && notes.size() <= 5
                query =~ /^traced-\d+$/
                secret == 'traced-secret'
                new BigDecimal(pages) >= 0
                new BigDecimal(rating) >= 3 && new BigDecimal(rating) <= 5
                new BigDecimal(price) <= 0.5
                new BigDecimal(volume) >= 10 && new BigDecimal(volume) <= 20
                LocalDate.parse(released)
                launched == '2999-01-01'
                LocalDateTime.parse(startsAt)
                LocalTime.parse(opensAt)
                YearMonth.parse(issue)
                sprint ==~ /\d{4}-W\d{2}/
                contact ==~ /traced-\d+@example\.com/
                (shortContact ==~ /[^@]+@example\.com/) && shortContact.size() <= 16
                edgeContact ==~ /\d@example\.com/
                tinyContact == 'kept'
                new URI(homepage).scheme == 'https' && new URI(homepage).host
                (shortHomepage ==~ /https:\/\/example\.com\/\d+/) && shortHomepage.size() <= 24
                siteHomepage == 'https://example.com'
                plainHomepage == 'http://example.com'
                ftpHomepage == 'ftp://example.com'
                (tinyHomepage ==~ /traced-\d+/) && tinyHomepage.size() <= 16
                phone ==~ /\d+/
                colour ==~ /#[0-9a-f]{6}/
            }

        and: 'each address and link passes the check the email and url constraints make, and stays on example.com'
            [sent.contact, sent.shortContact, sent.edgeContact].every { String email ->
                EmailValidator.instance.isValid(email) && email.endsWith('@example.com')
            }
            [sent.homepage, sent.shortHomepage, sent.siteHomepage, sent.plainHomepage, sent.ftpHomepage].every { String url ->
                URL_CONSTRAINT.isValid(url) && new UrlValidator().isValid(url) && new URI(url).host == 'example.com'
            }

        and: 'where no address or link to example.com fits, the field fails rather than naming another host'
            !URL_CONSTRAINT.isValid(sent.tinyHomepage)

        and: 'which is said, naming the field, unless the form was given a value for it'
            warned.contains('/typed has a field tinyHomepage too short')
            !warned.contains('field tinyContact')

        and: 'a hidden field is the page\'s own, so one that says nothing is sent saying nothing'
            sent._available == ''
    }

    void 'a select sends one of its options'() {
        given: 'an enum, an association and a date picker are rendered as selects, and only an option binds'
            TraceNativeMetadataTask task = task([], ['/select'])

        when:
            task.trace()

        then: 'the first option where none is selected, as a browser sends'
            Map<String, String> sent = postedTo('/select-save')
            sent.status == 'DRAFT'
            sent.author == 'null'

        and: 'the selected one where one is'
            sent.released_day == '9'
            sent.tags == '2'
            sent.released == 'date.struct'

        and: 'never one that cannot be chosen, nor one whose value only reads like the attribute'
            sent.format == 'HARDBACK'

        and: 'the text of an option that has no value, inside a group as much as outside one'
            sent.genre == 'Crime'

        and: 'and nothing for a select with no options, while the fields after it are still read'
            sent.nothing == ''
            sent.title =~ /^traced-\d+$/
    }

    void 'the form with the most fields is the one submitted'() {
        given: 'a page carrying a layout search form ahead of its own'
            TraceNativeMetadataTask task = task([], ['/create'])

        when:
            task.trace()

        then: 'taking the first would report a submission having recorded none of the binding'
            requested.any { it.startsWith('POST /save') }
            !requested.any { it.startsWith('POST /search') }
    }

    void 'a form can be named where the wanted one is not the fullest'() {
        given:
            TraceNativeMetadataTask task = task([], ['/create#searchForm'])

        when:
            task.trace()

        then:
            requested.any { it.startsWith('POST /search') }
            !requested.any { it.startsWith('POST /save') }
    }

    void 'naming a form that is not on the page fails the trace'() {
        given: 'rather than falling back to another and reporting that one as covered'
            TraceNativeMetadataTask task = task([], ['/create#nosuchform'])

        when:
            task.trace()

        then:
            GradleException e = thrown()
            e.message.contains("no form with id 'nosuchform'")
    }

    void 'a page behind a login is reached because the form is submitted first'() {
        given: 'the form listed after the path it makes reachable, to show the order is not the list'
            TraceNativeMetadataTask task = task(['/protected'], ['/login?username=admin&password=s3cret'])

        when:
            task.trace()

        then: 'the credentials given reach the application'
            requested.any { it.startsWith('POST /authenticate') && it.contains('username=admin') }

        and: 'and the protected page answers with itself rather than with the login page'
            requested.any { it == 'GET /protected' }
            !requested.any { it == 'GET /login' && requested.indexOf(it) > requested.findIndexOf { it.startsWith('POST /authenticate') } }
    }

    void 'a page answered from somewhere else is reported rather than counted'() {
        given: 'asked for without the login form that makes it reachable'
            TraceNativeMetadataTask task = task(['/protected'], [])

        when:
            task.trace()

        then: 'the redirect is followed, so what the agent recorded is the login page'
            requested.any { it == 'GET /login' }
    }

    void 'a form page answered from the login page does not count as tracing the form'() {
        given: 'only the protected form listed, so nothing has made it reachable'
            TraceNativeMetadataTask task = task([], ['/protected-form'])

        when:
            task.trace()

        then: 'the page it lands on has a form of its own, so every check after the redirect passes'
            GradleException e = thrown()
            e.message.contains('/protected-form')
            e.message.contains('answered from /login')

        and: 'and the login form it landed on was not submitted under the protected page\'s name'
            !requested.any { it.startsWith('POST /authenticate') }

        and: 'nor was the binding it was listed for ever exercised'
            !requested.any { it.startsWith('POST /protected-save') }
    }

    void 'and is traced once the form that makes it reachable is listed'() {
        given:
            TraceNativeMetadataTask task = task([],
                    ['/login?username=admin&password=s3cret', '/protected-form'])

        when:
            task.trace()

        then: 'the form on the page that was asked for, rather than the one on the way to it'
            requested.any { it.startsWith('POST /protected-save') && it.contains('secretTitle=') }
    }

    void 'a form on a page that has none fails the trace'() {
        given:
            TraceNativeMetadataTask task = task([], ['/'])

        when:
            task.trace()

        then: 'listed to be submitted and never submitted is a gap, not a success'
            GradleException e = thrown()
            e.message.contains('no form on the page')
    }

    void 'a path is not asked for while the application still answers that it is not ready'() {
        given: 'the Spring Security plugin answers every request with 503 and Retry-After until the application has started'
            TraceNativeMetadataTask task = task(['/'], [], 'gated')

        when:
            task.trace()

        then: 'where it would otherwise record the refusal and fail'
            notThrown(GradleException)
            requested.contains('GET /')
    }

    void 'an application whose home page answers 503 of its own accord is traced without waiting'() {
        given: 'a 503 without Retry-After, which does not say it will change'
            TraceNativeMetadataTask task = task(['/page'], [], 'homePageDown')
            task.startTimeoutSeconds.set(60)
            long began = System.nanoTime()

        when:
            task.trace()

        then: 'rather than after sitting out the whole of the start timeout'
            notThrown(GradleException)
            requested.contains('GET /page')
            Duration.ofNanos(System.nanoTime() - began) < Duration.ofSeconds(30)
    }

    void 'an application whose home page always says to come back is still traced once the timeout has passed'() {
        given: 'waited for no longer than the timeout'
            TraceNativeMetadataTask task = task(['/page'], [], 'homePageRetrying')
            task.startTimeoutSeconds.set(5)

        when:
            task.trace()

        then:
            notThrown(GradleException)
            requested.contains('GET /page')
    }

    void 'a path that answers with an error fails the trace'() {
        given: 'because what would be recorded is the error page rather than the page'
            TraceNativeMetadataTask task = task(['/broken'], [])

        when:
            task.trace()

        then:
            GradleException e = thrown()
            e.message.contains('did not answer with the page')
            e.message.contains('/broken')
    }

    void 'a form that fails to submit fails the trace'() {
        given:
            TraceNativeMetadataTask task = task([], ['/create'], 'save-fails')

        when:
            task.trace()

        then: 'a save that 500s during tracing records the failure and nothing of the save'
            GradleException e = thrown()
            e.message.contains('did not answer with the page')
    }

    void 'a request is made with the method, type, body and accept it names'() {
        given:
            TraceNativeMetadataTask task = task([], [])
            task.requests.set([[method: 'query', path: '/book/search', contentType: 'application/json',
                                body: '{"title":"Grails"}', accept: 'application/json']])

        when:
            task.trace()

        then: 'an action restricted to QUERY answers nothing else, so nothing else records what it reaches'
            requested.contains('QUERY /book/search {"title":"Grails"}')
            requested.contains('HEADERS application/json application/json')
    }

    void 'a request that names only its method and path sends no body, and accepts what a path does'() {
        given:
            TraceNativeMetadataTask task = task([], [])
            task.requests.set([[method: 'DELETE', path: '/book/1']])

        when:
            task.trace()

        then:
            requested.contains('DELETE /book/1')
            requested.contains('HEADERS null text/html,*/*')
    }

    void 'requests are made after the forms, whose session they are in'() {
        given: 'a request of a signed-in client is only worth making once the login form has been submitted'
            TraceNativeMetadataTask task = task(['/after'], ['/login'])
            task.requests.set([[method: 'QUERY', path: '/protected']])

        when:
            task.trace()

        then:
            requested.findIndexOf { it.startsWith('POST /authenticate') } < requested.indexOf('QUERY /protected')
            requested.indexOf('QUERY /protected') < requested.indexOf('GET /after')

        and: 'and it was not sent on to the login page'
            requested.count('GET /login') == 1
    }

    void 'a request that answers with an error fails the trace'() {
        given:
            TraceNativeMetadataTask task = task([], [])
            task.requests.set([[method: 'QUERY', path: '/broken']])

        when:
            task.trace()

        then:
            GradleException e = thrown()
            e.message.contains('QUERY /broken -> 500')
    }

    void 'a request that does not say what to send, or misspells it, fails the trace rather than sending something else'() {
        given: 'listed after one that can be made, and alongside a path'
            TraceNativeMetadataTask task = task(['/'], [])
            task.requests.set([[method: 'QUERY', path: '/book/valid'], request])

        when:
            task.trace()

        then: 'the one that cannot be made is named'
            GradleException e = thrown()
            e.message.contains('a request names its method and path')
            !e.message.contains('/book/valid')

        and: 'before the application is started, rather than once it has been started and traced'
            requested.isEmpty()
            !new File(task.temporaryDir, 'trace.log').exists()

        where:
            request << [[path: '/book/search'], [method: 'QUERY'], [method: 'QUERY', path: '/book/search', bodyy: '{}']]
    }

    void 'the cookie handler the build had is put back after a trace'() {
        given: 'a daemon outlives the build, so what this sets is handed to every task after it'
            CookieHandler inherited = new CookieManager()
            CookieHandler.default = inherited
            TraceNativeMetadataTask task = task(['/'], [])

        when:
            task.trace()

        then:
            CookieHandler.default.is(inherited)

        cleanup:
            CookieHandler.default = null
    }

    void 'and after one that failed'() {
        given:
            CookieHandler inherited = new CookieManager()
            CookieHandler.default = inherited
            TraceNativeMetadataTask task = task(['/broken'], [])

        when:
            task.trace()

        then:
            thrown(GradleException)
            CookieHandler.default.is(inherited)

        cleanup:
            CookieHandler.default = null
    }

    /** The fields a form was submitted with, by name, from what the application was sent. */
    private Map<String, String> postedTo(String action) {
        String line = requested.find { String request -> request.startsWith("POST ${action} ") }
        assert line, "nothing was posted to ${action}: ${requested}"
        line.substring("POST ${action} ".length()).split('&').collectEntries { String pair ->
            List<String> parts = pair.split('=', 2) as List<String>
            [(URLDecoder.decode(parts[0], 'UTF-8')): URLDecoder.decode(parts.size() > 1 ? parts[1] : '', 'UTF-8')]
        } as Map<String, String>
    }

    void 'a java without the agent is refused before the application is started'() {
        given: 'the agent ships with GraalVM, so the JDK running the build is usually not one'
            File plain = new File(temporaryFolder, 'plain-jdk/bin/java')
            plain.parentFile.mkdirs()
            plain.text = '#!/bin/sh\nexit 0\n'
            plain.setExecutable(true)
            TraceNativeMetadataTask task = task(['/'], [])
            task.javaExecutable.set(plain.absolutePath)

        when:
            task.trace()

        then: 'named, rather than left as a message about a library path from a JVM that would not load'
            GradleException e = thrown()
            e.message.contains('native-image-agent')
            e.message.contains('GraalVM')

        and: 'and nothing was run'
            requested.isEmpty()
    }
}
