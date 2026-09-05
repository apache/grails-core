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

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration

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
 * because on a JVM every bundle on the classpath is readable whether it was registered or not.</p>
 *
 * <p>Only the first of the three is covered by Spring Boot's own hints, which register the two
 * hardcoded patterns {@code messages.properties} and {@code messages_*.properties} and derive
 * nothing from the configured base names.</p>
 */
class Application extends GrailsAutoConfiguration {

    static void main(String[] args) {
        if (!args.contains('--i18n-check')) {
            GrailsApp.run(Application, args)
            return
        }

        ConfigurableApplicationContext context = (ConfigurableApplicationContext) GrailsApp.run(Application, args)
        try {
            MessageSource messages = context.getBean(MessageSource)

            // The application's own bundle, base name 'messages' — the one case Boot's hints cover.
            check(messages, 'native.app.greeting', Locale.ENGLISH, 'hello from the application bundle')

            // A plugin's bundle, base name 'native-messages'. Namespaced, so Boot's messages*
            // patterns never reach it; it survives only because the base names are recorded at build
            // time and hinted from them. The plugin name is also multi-word, so this covers the
            // descriptor's hyphenated spelling being matched against the plugin's camel-case one.
            check(messages, 'native.plugin.greeting', Locale.ENGLISH, 'hello from the plugin bundle')

            // A base name the application configured itself, written in dotted form, so this proves
            // config.i18n.custom was converted to config/i18n/custom before being used.
            check(messages, 'native.custom.greeting', Locale.ENGLISH, 'hello from a configured base name')

            // A locale variant of each. In an image this is what would fail if the hints registered
            // bundles instead of resource patterns and the image were built with a narrower locale
            // set than the bundles cover.
            check(messages, 'native.app.greeting', Locale.FRENCH, 'bonjour depuis le paquet de l application')
            check(messages, 'native.plugin.greeting', Locale.FRENCH, 'bonjour depuis le paquet du plugin')
            check(messages, 'native.custom.greeting', Locale.FRENCH, 'bonjour depuis un nom de base configure')

            // Not a base name, so this proves the rest of MessageSourceProperties was bound too:
            // the whole object binds at once or not at all.
            String unknown = messages.getMessage('no.such.code', null, Locale.ENGLISH)
            if (unknown != 'no.such.code') {
                throw new IllegalStateException('spring.messages.use-code-as-default-message did not reach ' +
                        "the message source; an unknown code gave '${unknown}'")
            }

            println 'i18n-check: every message bundle resolved'
        }
        finally {
            context.close()
        }
        System.exit(0)
    }

    private static void check(MessageSource messages, String code, Locale locale, String expected) {
        String actual
        try {
            actual = messages.getMessage(code, null, locale)
        }
        catch (Exception ex) {
            throw new IllegalStateException("'${code}' did not resolve for ${locale}; its bundle was never " +
                    "registered (${ex.class.simpleName})", ex)
        }
        if (actual != expected) {
            throw new IllegalStateException(
                    "'${code}' for ${locale} resolved to '${actual}', expected '${expected}'")
        }
    }
}
