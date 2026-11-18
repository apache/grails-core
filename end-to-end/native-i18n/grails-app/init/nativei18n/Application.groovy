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

package nativei18n

import groovy.transform.CompileStatic

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
import grails.util.Environment

import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.MessageSource

/**
 * Resolves one message from each way a bundle reaches the message source, and fails if any of them
 * does not resolve.
 *
 * <p>Run on a JVM this proves two things. That every bundle is reachable at all — which is a real
 * guard, because Spring Boot's message source is configured from {@code MessageSourceProperties},
 * and a bean created before {@code ConfigurationPropertiesBindingPostProcessor} is registered keeps
 * its defaults, silently losing every base name except {@code messages}. And, when compiled into a
 * GraalVM binary, that each bundle was registered as a resource — a failure no JVM run can produce,
 * because on a JVM every bundle on the classpath is readable whether it was registered or not. A
 * bundle missing from an image surfaces as a code resolving to itself, not as an exception, because
 * {@code spring.messages.use-code-as-default-message} is on.</p>
 *
 * <p>Only the first of the three is covered by Spring Boot's own hints, which register the two
 * hardcoded patterns {@code messages.properties} and {@code messages_*.properties} and derive
 * nothing from the configured base names. Grails registers every properties file at the root of the
 * classpath as well, which is where an application's and a plugin's bundles land, so of the three
 * only the configured base name, which is not at the root, is carried into an image by the hints
 * derived from {@code spring.messages.basename} alone.</p>
 *
 * <p>It also reads a value from the plugin's {@code plugin.yml}, which on a JVM is found beside the plugin's
 * classes and in an image only through the copy the build makes of it under the plugin's name.</p>
 *
 * <p>Every run is told which environment it should have started in, through the property
 * {@code i18n.check.environment}, and fails in any other. An image reads its environment from the build
 * information packaged into it, and a run on a JVM from {@code grails.env}, which the Grails Gradle plugin
 * sets to development for every task that runs an application. Either going wrong would check a
 * different startup from the one intended.</p>
 *
 * <p>Compiled statically, as a generated application's {@code Application} class is, so that the
 * image depends on resource hints for what this checks and not on reflection metadata for how it
 * checks it.</p>
 */
@CompileStatic
class Application extends GrailsAutoConfiguration {

    static void main(String[] args) {
        if (!args.contains('--i18n-check')) {
            GrailsApp.run(Application, args)
            return
        }

        ConfigurableApplicationContext context = GrailsApp.run(Application, args)
        int exitCode = 0
        try {
            String expectedEnvironment = context.environment.getProperty('i18n.check.environment')
            if (Environment.current.name != expectedEnvironment) {
                throw new IllegalStateException("The application started in ${Environment.current.name}, " +
                        "not in ${expectedEnvironment}, the environment i18n.check.environment names")
            }

            MessageSource messages = context.getBean(MessageSource)

            // The application's own bundle, base name 'messages' — the one case Boot's hints cover.
            check(messages, 'native.app.greeting', Locale.ENGLISH, 'hello from the application bundle')

            // A plugin's bundle, base name 'native-messages'. Namespaced, so Boot's messages*
            // patterns never reach it. Its file is at the root, which Grails registers, so what this
            // proves in an image is that the plugin is found and its base name reaches the message
            // source. The plugin name is also multi-word, so this covers the descriptor's hyphenated
            // spelling being matched against the plugin's camel-case one.
            check(messages, 'native.plugin.greeting', Locale.ENGLISH, 'hello from the plugin bundle')

            // A base name the application configured itself, outside the root and written in dotted
            // form. The one bundle only the hints derived from the base names carry into an image,
            // so this proves config.i18n.custom was converted to config/i18n/custom and registered.
            check(messages, 'native.custom.greeting', Locale.ENGLISH, 'hello from a configured base name')

            // A locale variant of each. In an image this is what would fail if the hints registered
            // bundles instead of resource patterns and the image were built with a narrower locale
            // set than the bundles cover.
            check(messages, 'native.app.greeting', Locale.FRENCH, "bonjour depuis le paquet de l'application")
            check(messages, 'native.plugin.greeting', Locale.FRENCH, 'bonjour depuis le paquet du plugin')
            check(messages, 'native.custom.greeting', Locale.FRENCH, 'bonjour depuis un nom de base configure')

            // Not a base name, so this proves the rest of MessageSourceProperties was bound too:
            // the whole object binds at once or not at all.
            String unknown = messages.getMessage('no.such.code', null, Locale.ENGLISH)
            if (unknown != 'no.such.code') {
                throw new IllegalStateException('spring.messages.use-code-as-default-message did not reach ' +
                        "the message source; an unknown code gave '${unknown}'")
            }

            // Not a bundle, but the plugin's own configuration, read from its plugin.yml. Here because the
            // image is built anyway: on a JVM the file is found beside the plugin's classes, in its JAR, and
            // an image has no JARs, so in one this proves the copy the build makes under the plugin's name
            // was carried into it.
            String configured = context.environment.getProperty('native.plugin.configuration')
            if (configured != 'from the plugin configuration') {
                throw new IllegalStateException("The plugin's plugin.yml did not reach the environment; " +
                        "native.plugin.configuration was '${configured}'")
            }

            println "i18n-check: in ${expectedEnvironment}, every message bundle resolved, and the plugin configuration was read"
        }
        catch (Throwable failure) {
            failure.printStackTrace()
            exitCode = 1
        }
        finally {
            context.close()
        }
        // Exit explicitly rather than letting a throwable leave main, which would make the exit code
        // wait on every non-daemon thread the application started - more of a risk for the binary
        // than for the jar.
        System.exit(exitCode)
    }

    /**
     * {@code spring.messages.use-code-as-default-message} is on, so a bundle that never reached the
     * message source does not raise anything — the code comes back as its own value. That is the
     * shape a missing resource registration takes in an image, so it is reported as itself rather
     * than as a plain mismatch.
     */
    private static void check(MessageSource messages, String code, Locale locale, String expected) {
        String actual = messages.getMessage(code, null, locale)
        if (actual == code) {
            throw new IllegalStateException("'${code}' did not resolve for ${locale}; it came back as its own " +
                    'code, so its bundle never reached the message source')
        }
        if (actual != expected) {
            throw new IllegalStateException(
                    "'${code}' for ${locale} resolved to '${actual}', expected '${expected}'")
        }
    }
}
