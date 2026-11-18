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
package org.grails.gradle.plugin.views

import java.util.concurrent.Callable

import groovy.transform.CompileStatic

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Transformer
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory

import grails.util.Environment

/**
 * The Grails environment a build compiles its views for.
 *
 * <p>Pages, JSON views and markup views are compiled ahead of time by a forked compiler that reads
 * the application's configuration for the environment it runs in, so what a view compiles to - the
 * codecs a page carries, for one - depends on that environment. The compile tasks state the
 * environment as an input and hand it to the compiler, rather than leaving the compiler to whatever
 * it inherits. This is where the input's default comes from, and how the input is read.</p>
 *
 * @since 7.0.18
 */
@CompileStatic
final class ViewCompileEnvironment {

    /** The JVM argument a build gave the compiler its environment by, before the task had an input for it. */
    private static final String STATED_ARGUMENT = "-D${Environment.KEY}"

    /** The environments Grails has configuration blocks of its own for, by whichever name it accepts. */
    private static final List<Environment> KNOWN = [Environment.DEVELOPMENT, Environment.PRODUCTION, Environment.TEST].asImmutable()

    private ViewCompileEnvironment() {
    }

    /**
     * The default for a compile task's {@code grailsEnvironment}: an environment the build gave the
     * compiler itself, as a {@code -Dgrails.env} JVM argument among the fork options of
     * {@code options}, which is how a build chose the environment before the task had an input for
     * it, else {@link #ofBuild the one the build runs with}.
     *
     * @param providers the build's providers
     * @param options the task's compile options, whose fork options may name the environment
     * @return the name of the environment, never absent
     */
    static Provider<String> defaultFor(ProviderFactory providers, ViewCompileOptions options) {
        providers.provider({ -> statedIn(options.forkOptions.jvmArgs) } as Callable<String>)
                .orElse(ofBuild(providers))
    }

    /**
     * The environment the build runs with: the {@code grails.env} system property given to Gradle,
     * else the {@code GRAILS_ENV} environment variable, and {@code development} where neither is
     * set, which is what the compiler has always fallen back to, as the application does.
     *
     * @param providers the build's providers, which read the property and the variable
     * @return the name of the environment, never absent
     */
    static Provider<String> ofBuild(ProviderFactory providers) {
        resolve(providers.systemProperty(Environment.KEY), providers.environmentVariable(Environment.ENV_KEY))
    }

    /**
     * The environment {@code stated} names, else the one {@code inherited} names, else
     * {@code development}. Each is read as {@link #canonicalName} reads it, so a blank one counts as
     * not set and leaves the choice to the next, as the application's own resolution does.
     *
     * @param stated the name the build was given, the {@code grails.env} system property
     * @param inherited the name the build inherited, the {@code GRAILS_ENV} environment variable
     * @return the name of the environment, never absent
     */
    static Provider<String> resolve(Provider<String> stated, Provider<String> inherited) {
        Transformer<String, String> canonical = { String name -> canonicalName(name) } as Transformer<String, String>
        stated.map(canonical)
                .orElse(inherited.map(canonical))
                .orElse(Environment.DEVELOPMENT.name)
    }

    /**
     * The environment a compile task states to the compiler: its {@code grailsEnvironment}, by the
     * environment's full name. A blank one fails the build: the compiler would read an environment
     * from what it inherits instead, which the input would not record.
     *
     * @param stated the task's {@code grailsEnvironment}
     * @param task the path of the task, for the message
     * @return the environment's full name
     */
    static String forCompiler(Provider<String> stated, String task) {
        String name = canonicalName(stated.getOrNull())
        if (name == null) {
            throw new InvalidUserDataException("The grailsEnvironment of ${task} is blank; it has to name the environment the views are compiled for")
        }
        name
    }

    /**
     * The environment a build gave the compiler itself, as a {@code -Dgrails.env} JVM argument among
     * the fork options of its compile task. The last one wins, as it does on a JVM command line.
     *
     * @param jvmArgs the JVM arguments of the compiler, possibly null
     * @return the environment's full name, or null where none is given or the one given is blank
     */
    static String statedIn(List<String> jvmArgs) {
        String argument = arguments(jvmArgs).reverse().find { String arg -> statesEnvironment(arg) }
        argument == null ? null : canonicalName(argument.substring(Math.min(argument.length(), STATED_ARGUMENT.length() + 1)))
    }

    /**
     * {@code jvmArgs} without any {@code -Dgrails.env} among them: the task states the environment to
     * the compiler from its input, which that argument was read into, so that the input is what the
     * compiler gets whatever the argument says.
     *
     * @param jvmArgs the JVM arguments of the compiler, possibly null
     * @return the arguments to start the compiler with
     */
    static List<String> withoutStated(List<String> jvmArgs) {
        arguments(jvmArgs).findAll { String arg -> !statesEnvironment(arg) }
    }

    /**
     * The arguments as strings. A build script may well write one as a GString, which the list's
     * type does not keep out and the compiler's command line takes as its text.
     */
    private static List<String> arguments(List<String> jvmArgs) {
        jvmArgs == null ? [] : jvmArgs.collect { Object arg -> String.valueOf(arg) }
    }

    /** Whether {@code arg} states the environment, with a value or, as a bare {@code -Dgrails.env}, without. */
    private static boolean statesEnvironment(String arg) {
        arg == STATED_ARGUMENT || arg.startsWith(STATED_ARGUMENT + '=')
    }

    /**
     * The full name of the environment {@code name} refers to, read as the application reads the
     * name it is started with: {@code dev}, {@code prod} and {@code test} stand for the environments
     * they are short for, the full names in any case for theirs, and any other name - {@code PROD}
     * among them - is a custom environment, whose configuration block is named by it as given.
     * {@code null} where {@code name} is blank, so that a blank setting counts as none.
     *
     * @param name the name as stated
     * @return the environment's full name, or null for a blank name
     */
    static String canonicalName(String name) {
        String trimmed = name?.trim()
        if (!trimmed) {
            return null
        }
        Environment environment = Environment.getEnvironment(trimmed)
        environment in KNOWN ? environment.name : trimmed
    }
}
