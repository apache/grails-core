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

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import java.util.regex.Pattern

import groovy.transform.CompileStatic
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Runs the application under GraalVM's tracing agent and writes down the reflection it did.
 *
 * <p>{@code GenerateNativeMetadataTask} records an application's own artefacts by reading the build
 * output, which needs no run and misses nothing of the application's. What it cannot know is the
 * framework's own reflection along a request path -- a controller method reached through Groovy's
 * dispatch, a conversion asked for while binding a form -- because none of that is in the
 * application's classes. An image built without it starts, serves its home page, and fails on the
 * request that first takes such a path.</p>
 *
 * <p>The agent records exactly that, and records only what ran. So the paths are declared rather
 * than discovered, and what is not listed is not covered:</p>
 *
 * <pre>
 * grails {
 *     nativeMetadata {
 *         paths = ['/', '/login', '/book', '/book/create']
 *         forms = ['/book/create']
 *     }
 * }
 * </pre>
 *
 * <p>A form is asked for, read, and submitted with the fields it declares, because posting fewer
 * than it declares records less than the application does. A checkbox is a string on the wire and a
 * boolean on the domain class, so a form submitted without one never asks the conversion service
 * anything -- and an image built from that trace fails the first time someone ticks a box.</p>
 *
 * <p>Forms are submitted before paths are asked for, and the session one establishes is carried
 * through the rest of the trace. A page behind a login is reached by listing the login form, which
 * makes the pages named after it reachable:</p>
 *
 * <pre>
 * grails {
 *     nativeMetadata {
 *         forms = ['/login?username=admin&amp;password=secret', '/book/create']
 *         paths = ['/', '/book']
 *     }
 * }
 * </pre>
 *
 * <p>Where a page carries more than one form -- a layout's search or sign-out beside the page's own
 * -- the one declaring the most fields is submitted, and which it was is reported. A page whose
 * wanted form is not the fullest names it: {@code '/book/create#bookForm'}.</p>
 *
 * <p>Written to the application's sources rather than to the build directory: what an image was
 * built from should be reviewable, and a trace is only as good as the paths someone thought of.</p>
 *
 * @since 8.0
 */
@CompileStatic
@DisableCachingByDefault(because = 'Records what a run of the application did, which is not an output of its inputs')
abstract class TraceNativeMetadataTask extends DefaultTask {

    /** How the agent is asked for, and the only way its output can be merged with what is there. */
    private static final String AGENT = 'native-image-agent'

    /** The names the agent library goes by, one of which is beside a GraalVM's java. */
    private static final List<String> AGENT_LIBRARIES = [
            'libnative-image-agent.dylib', 'libnative-image-agent.so', 'native-image-agent.dll'
    ]

    private static final Pattern FORM = Pattern.compile(/(?is)<form\b[^>]*>.*?<\/form>/)
    private static final Pattern ACTION = Pattern.compile(/(?i)\baction\s*=\s*"([^"]*)"/)
    private static final Pattern ID = Pattern.compile(/(?i)\bid\s*=\s*"([^"]+)"/)
    private static final Pattern FIELD = Pattern.compile(/(?is)<(?:input|select|textarea)\b[^>]*>/)
    private static final Pattern NAME = Pattern.compile(/(?i)\bname\s*=\s*"([^"]+)"/)
    private static final Pattern VALUE = Pattern.compile(/(?i)\bvalue\s*=\s*"([^"]*)"/)
    private static final Pattern TYPE = Pattern.compile(/(?i)\btype\s*=\s*"([^"]+)"/)

    /**
     * Carries the session from one request to the next, which is what makes a form that
     * authenticates worth submitting: the pages that follow it are only reachable once it has been.
     *
     * <p>Its own cookie store rather than the JVM's, so two traces at once cannot share a session
     * and none of it is global to the daemon.</p>
     *
     * <p>Built for the run and closed when it ends. The client owns a selector thread and an
     * executor, which do not stop being owned when the build does -- so it is closed rather than
     * left for the daemon to carry until it exits.</p>
     */
    private HttpClient client

    /** The archive to run, which has to be the one the image will be built from. */
    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract RegularFileProperty getArchiveFile()

    /**
     * A java from a GraalVM. The agent ships with GraalVM rather than with a JDK, and an application
     * built for an image is compiled for GraalVM's Java -- so the JDK that runs the build is usually
     * neither, and running the trace on it fails at load or refuses the class file.
     */
    @Input
    abstract Property<String> getJavaExecutable()

    @Input
    abstract ListProperty<String> getJvmArguments()

    /** Paths to ask for. */
    @Input
    abstract ListProperty<String> getPaths()

    /** Pages whose form is to be filled in and submitted. */
    @Input
    abstract ListProperty<String> getForms()

    @Input
    abstract Property<Integer> getPort()

    @Input
    abstract Property<Integer> getStartTimeoutSeconds()

    /**
     * Where the agent merges what it recorded. Not declared as an output: it is in the application's
     * sources, and a directory Gradle believes it owns is a directory Gradle will delete.
     */
    @Internal
    abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void trace() {
        File java = new File(javaExecutable.get())
        File metadata = outputDirectory.get().asFile
        metadata.mkdirs()
        refuseWithoutAgent(java)
        RunPort.refuseWhereTaken(port.get(), 'Tracing what an image needs', 'grails.nativeMetadata.port')

        List<String> command = []
        command << java.absolutePath
        command << "-agentlib:${AGENT}=config-merge-dir=${metadata.absolutePath}".toString()
        command.addAll(jvmArguments.get())
        command << '-jar' << archiveFile.get().asFile.absolutePath
        command << "--server.port=${port.get()}".toString()

        File output = new File(temporaryDir, 'trace.log')
        Process process = new ProcessBuilder(command)
                .directory(archiveFile.get().asFile.parentFile)
                .redirectErrorStream(true)
                .redirectOutput(output)
                .start()

        client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build()

        List<Answer> answered = []
        try {
            awaitStarted(process, output)
            // Forms first, then paths. A form that authenticates is what makes the pages after it
            // reachable at all: asked for beforehand, a protected page answers with a redirect to
            // the login form, and what the agent records is the login page under the name of the
            // page that was wanted. The session the form establishes is carried by this client.
            forms.get().each { String path -> answered << submit(path) }
            paths.get().each { String path -> answered << ask(path) }
        }
        finally {
            stop(process)
            // Closed with the run that needed it. The client owns a selector thread and an executor,
            // and the daemon outlives the build -- so a client left open is a set of threads left
            // running, and another set for every later build that traces again.
            client.close()
            client = null
        }

        answered.each { Answer answer -> logger.lifecycle('  {}', answer.line) }
        answered.findAll { Answer answer -> answer.elsewhere }.each { Answer answer ->
            logger.warn('{} was answered somewhere other than where it was asked for, so what was ' +
                    'recorded is that page and not this one. A page behind a login is reached by ' +
                    'listing its form in nativeMetadata.forms.', answer.line)
        }

        List<Answer> failed = answered.findAll { Answer answer -> !answer.recorded }
        if (failed) {
            throw new GradleException('The application did not answer with the page while being ' +
                    'traced, so what was recorded is not what was asked for:\n  ' +
                    "${failed*.line.join('\n  ')}\nWhat it printed is in ${output}")
        }
        logger.lifecycle('Traced {} forms and {} paths into {}',
                forms.get().size(), paths.get().size(), metadata)
    }

    /** What one request recorded, and whether it recorded the thing it was asked for. */
    private static class Answer {

        final String line

        /** Whether the page asked for is the page the agent saw. */
        final boolean recorded

        /** Whether the answer came from somewhere other than the path asked for. */
        final boolean elsewhere

        Answer(String line, boolean recorded, boolean elsewhere = false) {
            this.line = line
            this.recorded = recorded
            this.elsewhere = elsewhere
        }
    }

    /**
     * Refuses before starting rather than after. Without the agent the JVM stops at load with a
     * message about a library path, which reads as a broken machine rather than as the wrong JDK.
     */
    private static void refuseWithoutAgent(File java) {
        File home = java.parentFile?.parentFile
        boolean present = home != null && AGENT_LIBRARIES.any { String library ->
            new File(home, "lib/${library}").isFile()
        }
        if (!present) {
            throw new GradleException("${java} has no ${AGENT}, which ships with GraalVM rather than " +
                    'with a JDK. Point the project toolchain at a GraalVM, or set ' +
                    'grails.nativeMetadata.javaExecutable at one.')
        }
    }

    /**
     * Asks for a path, reading the body so that rendering it is part of what was recorded.
     *
     * <p>Where the answer came from is reported as well as what it was. A redirect is followed, so
     * an application that sends {@code /} to its real home page records that page -- but a page
     * that answers from somewhere else because the request was not allowed to reach it records the
     * wrong thing under the right name, and only saying where it landed tells the two apart.</p>
     */
    private Answer ask(String path) {
        HttpResponse<String> response = client.send(
                requestTo(path).GET().build(), HttpResponse.BodyHandlers.ofString())
        String asked = uriOf(path).path
        String landed = response.uri().path
        boolean elsewhere = landed != asked
        String where = elsewhere ? "  (landed on ${landed})" : ''
        new Answer("GET  ${path} -> ${response.statusCode()}${where}".toString(),
                response.statusCode() < 400, elsewhere)
    }

    /**
     * Fills in the form on a page and submits it to wherever the form says.
     *
     * <p>The fields come from the page rather than from a list here, because a list here is a list
     * to keep up to date, and the one thing a trace must not do is submit less than the form does.</p>
     */
    private Answer submit(String declared) {
        // A form may say what to put in it, and which of them it is:
        //   /login?username=admin&password=...#loginForm
        // Most need neither. A generated value beats one to keep up to date -- but a form that
        // authenticates is only worth submitting with credentials that work, and what it does on
        // success is the half worth recording.
        int fragment = declared.indexOf('#')
        String named = fragment < 0 ? null : declared.substring(fragment + 1)
        String withoutId = fragment < 0 ? declared : declared.substring(0, fragment)
        int query = withoutId.indexOf('?')
        String path = query < 0 ? withoutId : withoutId.substring(0, query)
        Map<String, String> given = [:]
        if (query >= 0) {
            for (String pair : withoutId.substring(query + 1).split('&')) {
                if (!pair) {
                    continue
                }
                int equals = pair.indexOf('=')
                String name = URLDecoder.decode(equals < 0 ? pair : pair.substring(0, equals), 'UTF-8')
                given[name] = equals < 0 ? '' : URLDecoder.decode(pair.substring(equals + 1), 'UTF-8')
            }
        }

        HttpResponse<String> page = client.send(
                requestTo(path).GET().build(), HttpResponse.BodyHandlers.ofString())
        if (page.statusCode() >= 400) {
            return new Answer("FORM ${path} -> ${page.statusCode()} asking for the page".toString(), false)
        }
        // Where the page came from, not only that one did. A form page reached without whatever
        // makes it reachable answers from the login form instead, and every check after this one
        // passes: that page has a form, its fields are filled in, it posts, and it answers below
        // 400. What would be reported is the login form submitted a second time under the name of
        // the page that was wanted, while the binding this was listed for went unexercised.
        //
        // Failed rather than warned, which is where this differs from asking for a path. A path
        // that redirects still traced a page, and an application is free to send one page to
        // another; a form that redirects traced a different form, and there is no reading of that
        // which is what was asked for.
        String landed = page.uri().path
        if (landed != uriOf(path).path) {
            return new Answer("FORM ${path} -> answered from ${landed}, so the form there is not " +
                    'this one. A page behind a login is reached by listing its form first.', false)
        }
        List<String> markups = formsIn(page.body())
        if (!markups) {
            return new Answer("FORM ${path} -> no form on the page".toString(), false)
        }
        String markup = chooseForm(markups, named)
        if (markup == null) {
            return new Answer("FORM ${path} -> no form with id '${named}' on the page".toString(), false)
        }
        Matcher action = ACTION.matcher(markup)
        String target = action.find() && action.group(1) ? action.group(1) : path

        Map<String, String> fields = fieldsOf(markup)
        if (!fields) {
            return new Answer("FORM ${path} -> the form declares no fields".toString(), false)
        }
        // What was asked for wins, but only for a field the form has: a value for a field that is
        // not there was meant for a form that has changed, and silently posting it says nothing.
        given.each { String name, String value ->
            if (fields.containsKey(name)) {
                fields[name] = value
            }
            else {
                logger.warn('{} has no field named {}, so that value was not sent', path, name)
            }
        }

        String encoded = fields.collect { String name, String value ->
            "${URLEncoder.encode(name, 'UTF-8')}=${URLEncoder.encode(value, 'UTF-8')}"
        }.join('&')

        HttpResponse<String> posted = client.send(
                requestTo(target)
                        .header('Content-Type', 'application/x-www-form-urlencoded')
                        .POST(HttpRequest.BodyPublishers.ofString(encoded))
                        .build(),
                HttpResponse.BodyHandlers.ofString())
        // Which form was submitted is said out loud. A page can carry more than one -- a layout's
        // search or sign-out beside the one that was meant -- and submitting the wrong one records
        // nothing of the binding it was listed for while reporting that it did.
        String which = describeChoice(markups, markup, named)
        new Answer("POST ${target} -> ${posted.statusCode()}  " +
                "(${fields.size()} fields, from ${path}${which})".toString(),
                posted.statusCode() < 400)
    }

    /** Every form on the page, in the order they appear. */
    private static List<String> formsIn(String html) {
        List<String> markups = []
        Matcher form = FORM.matcher(html ?: '')
        while (form.find()) {
            markups << form.group()
        }
        markups
    }

    /**
     * The form to submit: the one named, or the one declaring the most fields.
     *
     * <p>Named where a page carries more than one and the wanted one is not the fullest. Otherwise
     * the fullest is the better guess than the first: a layout's search box or sign-out button
     * comes before the page's own form and declares almost nothing, and taking the first meant a
     * trace that reported a submission having recorded none of the binding it was listed for.</p>
     */
    private static String chooseForm(List<String> markups, String named) {
        if (named) {
            return markups.find { String markup ->
                Matcher id = ID.matcher(markup)
                id.find() && id.group(1) == named
            }
        }
        markups.max { String markup -> fieldsOf(markup).size() }
    }

    /** How the chosen form is reported, said only where there was a choice to make. */
    private static String describeChoice(List<String> markups, String chosen, String named) {
        if (markups.size() == 1) {
            return ''
        }
        String position = " form ${markups.indexOf(chosen) + 1} of ${markups.size()}"
        named ? "${position} named '${named}'" : "${position}, the one with the most fields"
    }

    /**
     * What the form would send. A field that carries a value sends it, which is how the token a
     * security filter demands is carried; a checkbox sends what a ticked one sends; anything else
     * sends something recognisable, so a row in a database says where it came from.
     */
    private static Map<String, String> fieldsOf(String markup) {
        Map<String, String> fields = [:]
        Matcher field = FIELD.matcher(markup)
        while (field.find()) {
            String tag = field.group()
            Matcher name = NAME.matcher(tag)
            if (!name.find()) {
                continue
            }
            Matcher value = VALUE.matcher(tag)
            Matcher type = TYPE.matcher(tag)
            String kind = type.find() ? type.group(1).toLowerCase(Locale.ROOT) : 'text'
            if (kind in ['submit', 'button', 'reset', 'image', 'file']) {
                continue
            }
            fields[name.group(1)] = value.find() ? value.group(1)
                    : kind == 'checkbox' || kind == 'radio' ? 'on'
                    : kind == 'password' ? 'traced-secret'
                    : "traced-${System.nanoTime()}".toString()
        }
        fields
    }

    private HttpRequest.Builder requestTo(String path) {
        HttpRequest.newBuilder(uriOf(path))
                .timeout(Duration.ofSeconds(60))
                .header('Accept', 'text/html,*/*')
    }

    private URI uriOf(String path) {
        URI.create("http://localhost:${port.get()}${path.startsWith('/') ? path : '/' + path}")
    }

    private void awaitStarted(Process process, File output) {
        long deadline = System.currentTimeMillis() + Duration.ofSeconds(startTimeoutSeconds.get()).toMillis()
        StartupLog log = new StartupLog(output)
        while (System.currentTimeMillis() < deadline) {
            if (!process.isAlive()) {
                throw new GradleException('The application ended before it started serving. ' +
                        "What it printed is in ${output}")
            }
            if (log.saysItStarted()) {
                return
            }
            if (serving()) {
                return
            }
            Thread.sleep(250L)
        }
        throw new GradleException("The application did not start within ${startTimeoutSeconds.get()}s. " +
                "What it printed is in ${output}")
    }

    private boolean serving() {
        try {
            new Socket().withCloseable { Socket socket ->
                socket.connect(new InetSocketAddress('localhost', port.get()), 500)
                true
            }
        }
        catch (IOException ignored) {
            false
        }
    }

    /**
     * Asks the run to stop and waits for it. The agent writes what it recorded as the JVM exits, so
     * a run that is killed leaves the metadata as it found it.
     */
    private static void stop(Process process) {
        if (!process.isAlive()) {
            return
        }
        process.destroy()
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw new GradleException('The application did not stop, so the agent wrote nothing')
        }
    }
}
