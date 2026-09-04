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
package org.grails.plugins.i18n

import org.springframework.context.support.ResourceBundleMessageSource

import spock.lang.Specification
import spock.lang.TempDir

/**
 * Guards the development reload path.
 *
 * <p>Without a cache duration {@code ResourceBundleMessageSource} keeps every bundle in a map of its
 * own — "cache forever: prefer locale cache over repeated getBundle calls" — which
 * {@link ResourceBundle#clearCache} cannot reach, because that only clears the JDK's cache. Editing a
 * bundle would then have no effect until a restart, whereas the message source Grails used previously
 * reloaded automatically in development.</p>
 *
 * <p>{@link I18nEnvironmentPostProcessor} therefore contributes a short
 * {@code spring.messages.cache-duration} when reload is enabled; these tests pin down why that is
 * needed and that it works.</p>
 */
class MessageSourceReloadSpec extends Specification {

    @TempDir
    File bundleDir

    private ClassLoader bundleClassLoader

    void setup() {
        new File(bundleDir, 'reloadable.properties').text = 'a.code=first\n'
        bundleClassLoader = new URLClassLoader([bundleDir.toURI().toURL()] as URL[], null)
    }

    private ResourceBundleMessageSource messageSource(Long cacheMillis) {
        new ResourceBundleMessageSource().tap {
            setBasename('reloadable')
            bundleClassLoader = this.bundleClassLoader
            defaultEncoding = 'UTF-8'
            fallbackToSystemLocale = false
            if (cacheMillis != null) {
                setCacheMillis(cacheMillis)
            }
        }
    }

    private void editBundle() {
        File bundle = new File(bundleDir, 'reloadable.properties')
        bundle.text = 'a.code=second\n'
        bundle.setLastModified(System.currentTimeMillis() + 2000)
        ResourceBundle.clearCache(bundleClassLoader)
    }

    void 'without a cache duration an edited bundle is never re-read'() {
        given: "Boot's default leaves cacheMillis at -1, so Spring caches the bundle in a map of its own"
        ResourceBundleMessageSource messageSource = messageSource(null)

        expect:
        messageSource.getMessage('a.code', null, Locale.ENGLISH) == 'first'

        when:
        editBundle()

        then: 'clearing the JDK cache cannot reach Spring\'s own map — this is why the default is not enough'
        messageSource.getMessage('a.code', null, Locale.ENGLISH) == 'first'
    }

    void 'with a cache duration an edited bundle is picked up without a restart'() {
        given: 'the short duration the post processor contributes in development'
        ResourceBundleMessageSource messageSource = messageSource(5000L)

        expect:
        messageSource.getMessage('a.code', null, Locale.ENGLISH) == 'first'

        when:
        editBundle()

        then: 'Spring now calls ResourceBundle.getBundle per lookup, so the flush takes effect at once'
        messageSource.getMessage('a.code', null, Locale.ENGLISH) == 'second'
    }
}
