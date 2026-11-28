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
package org.grails.forge.cli

import java.util.function.Supplier
import groovy.transform.CompileStatic
import org.apache.grails.forge.core.ForgeEnvironment
import org.grails.forge.feature.FeatureSelectionException
import org.springframework.context.ApplicationContext
import org.springframework.context.ConfigurableApplicationContext

/**
 * The Forge application of the command line, started when it is first asked for and not before.
 * The application has the project generator and the features an application can be created with,
 * and starting it is most of the time a command takes; the commands that generate ask for it, and
 * the others, the help and the list of commands among them, never do. So a command that needs
 * nothing of the generator runs without it, and runs when the generator cannot be started.
 *
 * @since 9.0
 */
@CompileStatic
final class ForgeContext implements Supplier<ApplicationContext>, Closeable {

    private final Supplier<? extends ApplicationContext> starter
    private final boolean owned
    private ApplicationContext context
    // the system properties as they were before the application was started, which sets some, and right after
    private Properties before
    private Properties started
    private NotStartedException failure
    // Of the thread that describes: a context that serves several command lines at once, as that of grails mcp does,
    // lists the values of an option for one of them while another describes its commands without them.
    private final ThreadLocal<int[]> notListing = ThreadLocal.withInitial({ new int[1] } as Supplier<int[]>)

    /** Whether the JVM is the command line's own, which the interactive shell may end; not so where a program runs the command line in its own JVM. */
    boolean endsJvm = true

    /** The name the command line is started under, which its help shows; {@code null} for the name it has by itself. */
    String commandName

    /** The directories whose project something was said of once, and is not said of again while this lives. */
    final Set<String> advised = Collections.synchronizedSet(new HashSet<String>())

    /**
     * The application as {@link Application#builder} makes it, started on demand and closed by {@link #close}.
     */
    ForgeContext() {
        this({ Application.builder().run() } as Supplier<ApplicationContext>)
    }

    /**
     * @param starter starts the application; asked once, the first time the application is needed
     */
    ForgeContext(Supplier<? extends ApplicationContext> starter) {
        this.starter = starter
        this.owned = true
    }

    private ForgeContext(ApplicationContext running) {
        this.starter = null
        this.context = running
        this.owned = false
    }

    /**
     * @param running an application that runs already
     * @return that application, which is its owner's to close
     */
    static ForgeContext of(ApplicationContext running) {
        return new ForgeContext(running)
    }

    /**
     * @return the application, which is started now when it was not before
     * @throws NotStartedException when the application cannot be started, with one line that says so;
     * it is not tried again
     */
    @Override
    synchronized ApplicationContext get() {
        if (failure != null) {
            throw failure
        }
        if (context == null) {
            try {
                before = (Properties) System.getProperties().clone()
                context = starter.get()
                started = (Properties) System.getProperties().clone()
            } catch (RuntimeException | LinkageError e) {
                restoreEnvironment()
                String reason = (e.getMessage() ?: '').readLines().find { String line -> !line.trim().isEmpty() }
                failure = new NotStartedException('The project generator of the command line could not be started' +
                        " (${e.getClass().getSimpleName()}${reason == null ? '' : ': ' + FeatureSelectionException.shown(reason.trim())}). " +
                        'The commands that create an application or add a feature to one need it; the others do not', e)
                throw failure
            }
        }
        return context
    }

    /**
     * Does something that describes the commands without the application: where the values of an
     * option are those the application has, the description says where they are listed and the
     * application is not asked for them. So are the commands described for a tool, and so is a
     * command shown to be used after a mistake in the command line. It holds for what is done on this
     * thread: a command line that another thread runs on the same context at the same time lists them.
     *
     * @param action what is done
     * @return what it returns
     */
    <T> T withoutListing(Supplier<T> action) {
        int[] count = notListing.get()
        count[0]++
        try {
            return action.get()
        } finally {
            count[0]--
        }
    }

    private boolean isListing() {
        return notListing.get()[0] == 0
    }

    /**
     * @return whether the application was started
     */
    synchronized boolean isStarted() {
        return context != null
    }

    /**
     * Closes the application when it was started here, and takes away the system properties its start
     * added; one that was given running is left running.
     */
    @Override
    synchronized void close() {
        if (owned && context instanceof ConfigurableApplicationContext) {
            ((ConfigurableApplicationContext) context).close()
        }
        if (owned) {
            context = null
            restoreProperties()
        }
    }

    // Starting the application sets system properties, the environment of Grails among them, and a
    // JVM that runs more than this command line is left as it was: what the start set or changed is
    // put back, unless something else has changed it again since. Of a start that failed only the
    // environment is put back, see restoreEnvironment: what else it set is not known to be its own.
    private void restoreProperties() {
        if (before == null) {
            return
        }
        Properties was = before
        Properties set = started ?: new Properties()
        before = null
        started = null
        for (String name : set.stringPropertyNames()) {
            String earlier = was.getProperty(name)
            String byStart = set.getProperty(name)
            if (earlier != byStart && System.getProperty(name) == byStart) {
                if (earlier == null) {
                    System.clearProperty(name)
                } else {
                    System.setProperty(name, earlier)
                }
            }
        }
    }

    // The application makes the environment of Grails production before anything else is done for its
    // start (ForgeEnvironment), so a start that failed has done that whatever else it did or did not: it is
    // put back as soon as the start has failed, while the value is still the one the start gave it. The
    // command fails, and the program goes on with the environment it had.
    private void restoreEnvironment() {
        Properties was = before
        before = null
        started = null
        if (was == null) {
            return
        }
        String earlier = was.getProperty(ForgeEnvironment.PROPERTY)
        if (earlier != ForgeEnvironment.PRODUCTION && System.getProperty(ForgeEnvironment.PROPERTY) == ForgeEnvironment.PRODUCTION) {
            if (earlier == null) {
                System.clearProperty(ForgeEnvironment.PROPERTY)
            } else {
                System.setProperty(ForgeEnvironment.PROPERTY, earlier)
            }
        }
    }

    /**
     * The application could not be started. The message is one line for the user.
     */
    static final class NotStartedException extends IllegalStateException {

        NotStartedException(String message, Throwable cause) {
            super(message, cause)
        }
    }

    /**
     * The values an option can have that only the application knows, the features of a type of
     * application: asked of the application when they are listed, in the help of the command that has
     * the option or when its value is completed, and not when the command line is put together.
     * When the application cannot be started there are none.
     */
    static final class Candidates implements Iterable<String> {

        /** What stands for the values where they are not listed. */
        public static final String LISTED_ELSEWHERE = 'those the option --list-features lists'

        private final ForgeContext context
        private final Class<? extends Iterable> type

        Candidates(ForgeContext context, Class<? extends Iterable> type) {
            this.context = context
            this.type = type
        }

        /**
         * Describes something without the application, see {@link ForgeContext#withoutListing}.
         */
        <T> T withoutListing(Supplier<T> action) {
            return context.withoutListing(action)
        }

        @Override
        Iterator<String> iterator() {
            if (!context.isListing()) {
                return Collections.singletonList(LISTED_ELSEWHERE).iterator()
            }
            try {
                return ((Iterable<String>) context.get().getBean(type)).iterator()
            } catch (NotStartedException ignored) {
                // the help of the command is still its help, and the command says what is wrong when it is run
                return Collections.<String>emptyIterator()
            }
        }
    }
}
