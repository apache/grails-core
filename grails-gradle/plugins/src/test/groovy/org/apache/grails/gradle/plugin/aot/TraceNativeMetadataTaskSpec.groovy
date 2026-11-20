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

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Covers the run that records what an image will need.
 *
 * <p>The agent itself is GraalVM's and is not exercised here: what is worth covering is what the
 * task asks the application for, because the metadata is only ever as complete as that.</p>
 */
class TraceNativeMetadataTaskSpec extends Specification {

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
