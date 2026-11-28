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
package org.apache.grails.forge.cli.mcp.run

import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import java.util.function.Consumer
import groovy.transform.CompileStatic
import org.apache.grails.forge.cli.console.GradleRunner
import org.apache.grails.forge.cli.mcp.McpHandler
import org.apache.grails.forge.cli.mcp.McpTool
import org.apache.grails.forge.cli.mcp.McpToolSet

/**
 * Keeps the tools of {@code grails mcp} those of its directory as it is now. The tools are made from
 * {@code help --json}, which is run in the server's process when the server starts and whenever what the
 * tools depend on changes; the server never runs a build for it.
 *
 * <ul>
 * <li>What the tools depend on is told by a fingerprint of files of the directory and of its project:
 * whether each of {@link #FILES} is there, when it was changed and how long it is, whether each of
 * {@link #DIRECTORIES} is there, and the names of the files of {@code src/main/scripts}.</li>
 * <li>The fingerprint is looked at every so often, and right after each call of a tool that is not
 * read-only ({@link #check()}).</li>
 * <li>When it has changed the tools are made anew. Where their names, input schemas, descriptions or
 * annotations differ from those before, they replace them, and the clients are told that the list of the
 * tools changed; where the directory became a project, or stopped being one, they are told that the list
 * of the resources changed too.</li>
 * <li>While the tools are made anew, the tools before are given; before the first tools are made, they
 * are waited for, for a while.</li>
 * </ul>
 *
 * @since 9.0
 */
@CompileStatic
final class ChangeWatcher implements Closeable {

    /** The files whose change changes the tools: whether each is there, when it was changed, and how long it is. */
    public static final List<String> FILES = ['.grails/cache/tool-catalog.json', '.grails/project.yml', '.grails/forge.lock.yml', 'grails-forge-cli.yml',
                                              'grails-cli.yml', 'gradle.properties', 'build.gradle', 'build.gradle.kts', 'settings.gradle',
                                              'settings.gradle.kts'].asImmutable()

    /** The directories whose being there changes the tools. */
    public static final List<String> DIRECTORIES = ['grails-app', '.grails'].asImmutable()

    /** The directory whose files are tools by their names, the scripts of the application. */
    public static final String SCRIPTS = 'src/main/scripts'

    /** What the diagnostics of the tools say while none could be made. */
    public static final String NOT_LISTED = 'The commands of grails are not listed yet'

    private static final int MOST_SCRIPTS = 1000

    /**
     * Makes the tools of the directory as it is now.
     */
    interface Projection {

        /**
         * @param fingerprint the fingerprint of the files the tools depend on, taken before they are made
         * @return the tools
         * @throws Exception when they cannot be made, as when {@code help --json} fails
         */
        McpToolSet project(String fingerprint) throws Exception

    }

    private final Path directory
    private final Projection projection
    private final Duration pollEvery
    private final Duration firstWait
    private final Consumer<String> changed
    private final PrintStream diagnostics
    private final CountDownLatch first = new CountDownLatch(1)
    private final Object projecting = new Object()
    private final ScheduledExecutorService timer
    private volatile McpToolSet current
    private volatile boolean placeholderGiven
    // guarded by projecting
    private boolean failed
    private String failure

    /**
     * @param directory the directory of the server
     * @param projection what makes the tools
     * @param pollEvery how often the fingerprint is looked at
     * @param firstWait how long the first tools are waited for
     * @param changed told {@link McpHandler#TOOLS} or {@link McpHandler#RESOURCES} when that list changed
     * @param diagnostics where it is said that the tools could not be made
     */
    ChangeWatcher(Path directory, Projection projection, Duration pollEvery, Duration firstWait, Consumer<String> changed, PrintStream diagnostics) {
        this.directory = Objects.requireNonNull(directory, 'directory')
        this.projection = Objects.requireNonNull(projection, 'projection')
        this.pollEvery = Objects.requireNonNull(pollEvery, 'pollEvery')
        this.firstWait = Objects.requireNonNull(firstWait, 'firstWait')
        this.changed = Objects.requireNonNull(changed, 'changed')
        this.diagnostics = Objects.requireNonNull(diagnostics, 'diagnostics')
        this.timer = Executors.newSingleThreadScheduledExecutor({ Runnable runnable ->
            Thread thread = new Thread(runnable, 'grails-mcp-watch')
            thread.daemon = true
            thread
        } as ThreadFactory)
    }

    /**
     * Makes the first tools, in the background, and from then on looks at the fingerprint every so often.
     */
    void start() {
        long every = pollEvery.toMillis()
        timer.scheduleWithFixedDelay({ -> checkQuietly() } as Runnable, 0L, every, TimeUnit.MILLISECONDS)
    }

    /**
     * @return the tools: those made last, also while they are made anew; before the first are made, they
     * are waited for, and where they are not made in time, a set of no tools whose diagnostics say so
     */
    McpToolSet tools() {
        McpToolSet tools = current
        if (tools != null) {
            return tools
        }
        try {
            first.await(firstWait.toMillis(), TimeUnit.MILLISECONDS)
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt()
        }
        tools = current
        if (tools != null) {
            return tools
        }
        placeholderGiven = true
        return placeholder("${NOT_LISTED}: help --json has not ended yet".toString())
    }

    /**
     * Looks at the fingerprint now, and makes the tools anew when it has changed, or when they could not be
     * made the last time. The clients are told of a change of the list.
     *
     * @return whether the list of the tools changed
     * @throws InterruptedException when the thread is interrupted while the tools are made: they stay as they were
     */
    boolean check() throws InterruptedException {
        synchronized (projecting) {
            McpToolSet before = current
            String fingerprint = fingerprint(before)
            if (before != null && !failed && before.fingerprint == fingerprint) {
                return false
            }
            McpToolSet after
            try {
                after = projection.project(fingerprint)
            } catch (InterruptedException e) {
                throw e
            } catch (Exception e) {
                notMade(e)
                return false
            }
            failed = false
            failure = null
            current = after
            first.countDown()
            if (before == null) {
                if (placeholderGiven) {
                    // a client was given no tools while it waited: it is told that there are some now
                    changed.accept(McpHandler.TOOLS)
                    return true
                }
                return false
            }
            boolean toolsChanged = listed(before) != listed(after)
            if (toolsChanged) {
                changed.accept(McpHandler.TOOLS)
            }
            if (before.project != after.project) {
                changed.accept(McpHandler.RESOURCES)
            }
            return toolsChanged
        }
    }

    /**
     * Stops looking at the fingerprint.
     */
    @Override
    void close() {
        timer.shutdownNow()
    }

    /**
     * @return the fingerprint of the files the tools of the directory depend on now
     */
    String fingerprint() {
        return fingerprint(current)
    }

    // of the directory, and of the project it is in where that is another directory
    private String fingerprint(McpToolSet tools) {
        List<Path> bases = [directory]
        Object project = tools?.helpDocument?.get('projectDirectory')
        if (project instanceof String) {
            Path projectDirectory = Paths.get((String) project)
            if (projectDirectory != directory) {
                bases.add(projectDirectory)
            }
        }
        StringBuilder said = new StringBuilder()
        for (Path base : bases) {
            said.append('base ').append(base).append('\n')
            for (String name : FILES) {
                said.append(name).append(' ').append(attributes(base.resolve(name))).append('\n')
            }
            for (String name : DIRECTORIES) {
                said.append(name).append(' ').append(Files.isDirectory(base.resolve(name), LinkOption.NOFOLLOW_LINKS)).append('\n')
            }
            said.append(SCRIPTS).append(' ').append(scripts(base.resolve(SCRIPTS))).append('\n')
        }
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        return digest.digest(said.toString().getBytes(StandardCharsets.UTF_8)).encodeHex().toString()
    }

    private void checkQuietly() {
        try {
            check()
        } catch (InterruptedException ignored) {
            // the watch has ended
        } catch (RuntimeException e) {
            notMade(e)
        }
    }

    // the tools stay as they were, and are made again at the next look; before the first, a set of no tools says why
    private void notMade(Exception e) {
        synchronized (projecting) {
            failed = true
            String reason = GradleRunner.line(e.message ?: e.class.simpleName)
            if (reason != failure) {
                failure = reason
                diagnostics.println("| Warning grails mcp could not list the commands of its directory: ${reason}")
            }
            if (current == null) {
                current = placeholder("${NOT_LISTED}: ${reason}".toString())
                first.countDown()
            }
        }
    }

    // a set of no tools, whose fingerprint is no fingerprint of files, so that the next look makes the tools
    private McpToolSet placeholder(String diagnostic) {
        Map<String, Object> document = new LinkedHashMap<>()
        document.put('format', 1)
        document.put('projectDirectory', null)
        document.put('complete', false)
        document.put('diagnostics', [diagnostic])
        document.put('commands', [])
        return new McpToolSet([], document, false, '')
    }

    // what is compared of two lists of tools: the names, the input schemas, the descriptions and the annotations
    private static List<List<Object>> listed(McpToolSet tools) {
        return tools.tools.collect { McpTool tool -> [tool.name, tool.inputSchema, tool.description, tool.annotations] as List<Object> }
    }

    private static String attributes(Path file) {
        try {
            BasicFileAttributes read = Files.readAttributes(file, BasicFileAttributes, LinkOption.NOFOLLOW_LINKS)
            return "${read.lastModifiedTime().toMillis()} ${read.size()}".toString()
        } catch (NoSuchFileException ignored) {
            return 'none'
        } catch (IOException e) {
            return "unreadable ${e.class.simpleName}".toString()
        }
    }

    private static String scripts(Path scripts) {
        if (!Files.isDirectory(scripts, LinkOption.NOFOLLOW_LINKS)) {
            return 'none'
        }
        List<String> names = []
        try {
            DirectoryStream<Path> stream = Files.newDirectoryStream(scripts)
            try {
                for (Path each : stream) {
                    names.add(each.fileName.toString())
                    if (names.size() >= MOST_SCRIPTS) {
                        break
                    }
                }
            } finally {
                stream.close()
            }
        } catch (IOException e) {
            return "unreadable ${e.class.simpleName}".toString()
        }
        return names.sort().join('/')
    }
}
