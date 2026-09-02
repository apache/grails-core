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
package grails.boot.config

import org.springframework.aot.AotDetector
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.SpringProperties
import spock.lang.Specification

import org.apache.grails.core.aot.ArtefactClassesBeanFactoryInitializationAotProcessor

/**
 * Covers an application reading back the artefacts written down while its code was generated.
 *
 * <p>Both of the usual ways to find them need something an image does not have -- a classpath to
 * walk, or a list the compile-time transform built as it went -- so what was found while the code
 * was generated is registered as a singleton and read here.</p>
 */
class GrailsAutoConfigurationArtefactsSpec extends Specification {

    GenericApplicationContext context = new GenericApplicationContext()

    void cleanup() {
        SpringProperties.setProperty(AotDetector.AOT_ENABLED, null)
        context.close()
    }

    /** Registered the way the generated initializer registers it, before any definition is read. */
    private void written(Class<?>... artefacts) {
        context.beanFactory.registerSingleton(
                ArtefactClassesBeanFactoryInitializationAotProcessor.BEAN_NAME, artefacts)
    }

    private GrailsAutoConfiguration application() {
        new GrailsAutoConfiguration(applicationContext: context)
    }

    void 'the artefacts written down are what the application is made of'() {
        given:
            SpringProperties.setProperty(AotDetector.AOT_ENABLED, 'true')
            written(String, Integer)

        expect: 'rather than a scan, which in an image finds nothing at all'
            application().classes() == [String, Integer]
    }

    void 'nothing written down leaves them to be found the usual ways'() {
        given:
            SpringProperties.setProperty(AotDetector.AOT_ENABLED, 'true')

        expect: 'a context generated before this was written has no such singleton'
            application().classes() != null
    }

    void 'an ordinary start does not read them even where they are there'() {
        given:
            SpringProperties.setProperty(AotDetector.AOT_ENABLED, 'false')
            written(String, Integer)

        expect: 'the scan is the answer wherever scanning works, and stays the answer'
            application().classes() != [String, Integer]
    }
}
