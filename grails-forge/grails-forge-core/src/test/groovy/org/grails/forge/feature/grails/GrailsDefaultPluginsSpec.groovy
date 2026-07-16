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

package org.grails.forge.feature.grails

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.application.ApplicationType
import org.grails.forge.fixture.CommandOutputFixture
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.JdkVersion
import org.grails.forge.options.Options
import org.grails.forge.options.TestFramework

class GrailsDefaultPluginsSpec extends ApplicationContextSpec implements CommandOutputFixture {

    void "test that default grails plugins are present"() {
        given:
        final Map<String, String> output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final String buildGradle = output["build.gradle"]

        expect:
        buildGradle.contains("implementation \"org.apache.grails:grails-rest-transforms\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-databinding\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-services\"")
        buildGradle.contains("implementation \"org.apache.grails:grails-interceptors\"")
    }

    void "test i18n message properties files are present"() {
        given:
        final Map<String, String> output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))

        expect:
        output.containsKey("grails-app/i18n/messages.properties")
        !(Arrays.asList("cs", "da", "de", "es", "fr", "it", "ja", "nb", "nl", "pl", "pt_BR", "pt_PT", "ru", "sk", "sv", "th", "zh_CN")
                .findAll {prop -> { !output.containsKey("grails-app/i18n/messages_" + prop + ".properties") }})
    }

    void "test i18n bundles are key-complete and MessageFormat-safe"() {
        given:
        final Map<String, String> output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final Map<String, Properties> bundles = output.keySet()
                .findAll { it.startsWith("grails-app/i18n/") }
                .collectEntries { name ->
                    final Properties props = new Properties()
                    props.load(new StringReader(output[name]))
                    [name, props]
                }
        final Set<String> union = bundles.values().collectMany { it.stringPropertyNames() } as Set

        expect: "every locale declares every key, so no locale silently falls back to English"
        bundles.every { name, props ->
            final Set<String> missing = (union - props.stringPropertyNames()) as Set
            assert missing.isEmpty(), "${name} is missing ${missing}"
            true
        }

        and: "no bundle declares a key twice, since Properties silently keeps only the last value"
        bundles.keySet().every { name ->
            final List<String> keys = output[name].readLines()
                    .findAll { it && !it.startsWith('#') && it.contains('=') }
                    .collect { it.substring(0, it.indexOf('=')).trim() }
            final List<String> duplicated = keys.countBy { it }.findAll { it.value > 1 }*.key
            assert duplicated.isEmpty(), "${name} declares duplicate keys: ${duplicated}"
            true
        }

        and: "every parameterized message is a valid MessageFormat that actually substitutes its arguments"
        bundles.every { name, props ->
            props.stringPropertyNames().findAll { props.getProperty(it).contains('{') }.every { key ->
                final String formatted = new java.text.MessageFormat(props.getProperty(key))
                        .format([2, 2, 2, 2, 2] as Object[])
                assert !(formatted =~ /\{\d/), "${name} ${key} leaves placeholders unsubstituted: ${formatted}"
                true
            }
        }
    }

    void "test every i18n bundle translates the welcome page"() {
        given:
        final Map<String, String> output = generate(ApplicationType.WEB, new Options(DevelopmentReloading.DEVTOOLS))
        final List<String> suffixes = [""] + Arrays.asList("cs", "da", "de", "es", "fr", "it", "ja", "nb", "nl",
                "pl", "pt_BR", "pt_PT", "ru", "sk", "sv", "th", "zh_CN").collect { "_" + it }

        expect: "each bundle carries the welcome-page keys the default index.gsp renders through"
        suffixes.every { suffix ->
            final String bundle = output["grails-app/i18n/messages" + suffix + ".properties"]
            ["welcome.title", "welcome.congratulations", "welcome.controllers.title",
             "welcome.filter.name", "welcome.plugins.title"].every { key ->
                bundle.contains(key + "=")
            }
        }

        and: "spot-checked translations survive the template pipeline byte-intact"
        output["grails-app/i18n/messages_ru.properties"].contains("welcome.title=Добро пожаловать в Grails")
        output["grails-app/i18n/messages_ja.properties"].contains("welcome.title=Grailsへようこそ")
        output["grails-app/i18n/messages_th.properties"].contains("welcome.title=ยินดีต้อนรับสู่ Grails")
        output["grails-app/i18n/messages_zh_CN.properties"].contains("welcome.title=欢迎使用 Grails")
    }

}
