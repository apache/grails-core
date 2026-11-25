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
package org.apache.grails.forge.cli;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Loads, on threads beside the one that runs the command, classes the command line is about to load
 * itself. A command of the command line is a short-lived process, and most of its time is the JVM
 * reading, parsing and defining some two thousand classes one after the other on one thread while
 * the other processors of the computer are idle. Loading them ahead on a second thread took a
 * quarter to a third off the time of a command where it was measured.
 *
 * <p><strong>What is done.</strong></p>
 * <ul>
 * <li>The classes named in {@value #CLASSES}, a list in the jar of the command line of the classes
 * every command loads, are loaded <em>without being initialized</em>: {@code Class.forName(name,
 * false, loader)}. For a command that starts the project generator, the classes of
 * {@value #GENERATOR_CLASSES} are loaded after them. Loading a class runs none of its code, so no
 * static initializer runs earlier, on another thread or in another order than it does without this:
 * a class is initialized when the command first uses it, by the thread that uses it, as always.</li>
 * <li>One class is initialized: {@code org.codehaus.groovy.runtime.FormatHelper} of Groovy, which
 * every command initializes the first time it formats a value. Its initializer reads the default
 * imports of the Groovy compiler, which scans the image of the JDK and the Groovy jar and takes some
 * 55 ms. What it computes is constants of the Groovy runtime that depend on nothing a command does,
 * and it uses no class that the command line initializes with code of its own; it is started only
 * after this thread has initialized the Groovy runtime, so that the thread which initializes that is
 * the one it always was.</li>
 * </ul>
 *
 * <p><strong>What cannot go wrong.</strong> The threads are daemon threads: the JVM ends when the
 * command has, whether they are done or not. Whatever is thrown on them is caught and dropped, a
 * class that is not there, a list that is missing, cannot be read or names what the jar no longer
 * has among it: the command then loads what it needs itself, as it would have. Nothing is written
 * anywhere. It is not done at all where the JVM has one processor, where a second thread only takes
 * time from the first, or where the system property {@value #PROPERTY} is {@code false}.</p>
 *
 * <p>The lists are files of the sources, kept current by {@code PreloadClassesSpec} of
 * {@code grails-cli}, which fails when they have drifted from what the commands load and writes
 * them anew when asked to: a list made while the jar is built would make the jar depend on the
 * machine that builds it.</p>
 *
 * @since 9.0
 */
public final class StartupPreload {

    /** The system property that turns this off when it is {@code false}. */
    public static final String PROPERTY = "grails.cli.preload";

    /** The resource that lists the classes every command loads, one name on a line; a line that starts with {@code #} is a comment. */
    public static final String CLASSES = "META-INF/grails-forge-cli/preload-classes";

    /** The resource that lists the classes a command that starts the project generator loads besides. */
    public static final String GENERATOR_CLASSES = "META-INF/grails-forge-cli/preload-classes-generator";

    /** The commands that start the project generator whatever their arguments are. */
    public static final List<String> GENERATOR_COMMANDS = List.of("create-app", "create-webapp", "create-plugin", "create-web-plugin",
            "create-rest-api-plugin", "create-restapi");

    /**
     * The commands of a group that start the project generator when they run, by the name of the group:
     * {@code feature add}, {@code feature list}, {@code feature search}, {@code feature info} (also {@code feature show})
     * and {@code feature diff} read what it knows of the features, {@code project regenerate}
     * generates the application again and {@code project migrate} looks for the features of the application among
     * those it knows. Each of them is short-lived and spends most of its time starting the generator, so the
     * classes of the generator are loaded ahead for it as they are for the commands that create an application.
     */
    public static final Map<String, List<String>> GENERATOR_SUBCOMMANDS = Map.of(
            "feature", List.of("add", "list", "search", "info", "show", "diff"),
            "project", List.of("regenerate", "migrate"));

    private static final String GROOVY_RUNTIME = "groovy.lang.GroovySystem";
    // Reported to Groovy as GROOVY-9646 (https://issues.apache.org/jira/browse/GROOVY-9646): the static
    // initializer of this class starts a scan of the JDK image and of the Groovy jar that formatting does
    // not use. When a Groovy that Grails requires no longer does that, the thread that initializes it can go.
    private static final String FORMATTING = "org.codehaus.groovy.runtime.FormatHelper";

    private static final String STARTED = "started";

    // what start did, for the diagnostics of the command line
    private static volatile String status = "not started: the command line runs in the JVM of a program that did not start it";
    private static volatile List<Thread> running = List.of();

    private StartupPreload() {
    }

    /**
     * @return what was done, in a few words: whether the threads were started and, while they run,
     * that they still load; or why nothing was done. For {@link Diagnostics}.
     */
    public static String status() {
        String said = status;
        if (!said.startsWith(STARTED)) {
            return said;
        }
        boolean loading = running.stream().anyMatch(Thread::isAlive);
        return said + (loading ? ", still loading" : ", done");
    }

    /**
     * Starts the threads, unless it is turned off or the JVM has one processor. Called first thing by
     * a program that runs one command line and ends, with the class loader the command line is
     * loaded by; a caller that runs the command line as a library in a JVM of its own does not call it.
     *
     * @param loader the class loader of the command line
     * @param args the command line, which says whether the project generator will be started
     */
    public static void start(ClassLoader loader, String... args) {
        try {
            if ("false".equalsIgnoreCase(System.getProperty(PROPERTY))) {
                status = "off: the system property " + PROPERTY + " is false";
                return;
            }
            start(loader, Runtime.getRuntime().availableProcessors(), args);
        } catch (Throwable ignored) {
            // the command loads what it needs itself
        }
    }

    /**
     * @param processors how many processors the JVM has
     * @return the threads that were started, none where there is one processor
     */
    static List<Thread> start(ClassLoader loader, int processors, String... args) {
        List<Thread> started = new ArrayList<>();
        running = started;
        if (processors < 2 || loader == null) {
            status = processors < 2 ? "off: the JVM has one processor" : "off: there is no class loader";
            return started;
        }
        boolean generator = startsGenerator(args);
        status = STARTED + ": the classes every command loads" + (generator ? " and those of the project generator" : "");
        started.add(daemon("grails-preload-classes", () -> {
            load(loader, CLASSES);
            if (generator) {
                load(loader, GENERATOR_CLASSES);
            }
        }));
        try {
            // by this thread, which is the one that initializes the Groovy runtime without this as well
            Class.forName(GROOVY_RUNTIME, true, loader);
        } catch (Throwable ignored) {
            return started;
        }
        started.add(daemon("grails-preload-formatting", () -> Class.forName(FORMATTING, true, loader)));
        return started;
    }

    // whether the command line names a command that starts the project generator: the first word that is no option,
    // or that word and the next one for a command of a group
    private static boolean startsGenerator(String... args) {
        if (args == null) {
            return false;
        }
        List<String> words = Arrays.stream(args).filter(arg -> arg != null && !arg.startsWith("-")).limit(2).toList();
        if (words.isEmpty()) {
            return false;
        }
        if (GENERATOR_COMMANDS.contains(words.get(0))) {
            return true;
        }
        List<String> ofGroup = GENERATOR_SUBCOMMANDS.get(words.get(0));
        return ofGroup != null && words.size() > 1 && ofGroup.contains(words.get(1));
    }

    private static void load(ClassLoader loader, String resource) throws Exception {
        InputStream in = loader.getResourceAsStream(resource);
        if (in == null) {
            return;
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String name = line.trim();
                if (name.isEmpty() || name.charAt(0) == '#') {
                    continue;
                }
                try {
                    Class.forName(name, false, loader);
                } catch (Throwable ignored) {
                    // a name of an earlier version: the next one
                }
            }
        }
    }

    private static Thread daemon(String name, Work work) {
        Thread thread = new Thread(() -> {
            try {
                work.run();
            } catch (Throwable ignored) {
                // the command loads what it needs itself
            }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private interface Work {

        void run() throws Exception;
    }
}
