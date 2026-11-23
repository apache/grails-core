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
package org.grails.forge.util

import org.grails.forge.application.Project
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.options.TestFramework
import spock.lang.Specification
import spock.lang.Unroll

/**
 * The names the generator makes of the name of an application are the same in every language: the
 * names of classes, files and packages of a generated application do not depend on the language of
 * the computer it was generated on. In Turkish the upper case of {@code i} is not {@code I}.
 */
class NameUtilsLocaleSpec extends Specification {

    Locale locale

    void setup() {
        locale = Locale.getDefault()
    }

    void cleanup() {
        Locale.setDefault(locale)
    }

    @Unroll
    void "the names of #name are the same with the locale #tag"() {
        given:
        Locale.setDefault(Locale.ENGLISH)
        Project english = NameUtils.parse(name)
        Map<String, String> names = english.getProperties()
        Map<String, String> expected = made(name)

        when:
        Locale.setDefault(Locale.forLanguageTag(tag))
        Project other = NameUtils.parse(name)

        then:
        other.getProperties() == names
        made(name) == expected
        names.values().every { String each -> each.every { String letter -> letter.charAt(0) < 128 } }

        where:
        [name, tag] << [['com.example.inventoryIi', 'com.example.item-index', 'iii'], ['tr-TR', 'az-AZ', 'de-DE']].combinations()
    }

    void "the class name of an application whose name starts with i starts with I, in Turkish too"() {
        given:
        Locale.setDefault(Locale.forLanguageTag('tr-TR'))

        expect:
        NameUtils.parse('com.example.inventory').getProperties().get('className') == 'Inventory'
    }

    void "the values of the options are written the same in Turkish"() {
        given:
        List<String> english = DevelopmentReloading.values()*.toString() + TestFramework.values()*.toString()

        when:
        Locale.setDefault(Locale.forLanguageTag('tr-TR'))

        then:
        DevelopmentReloading.values()*.toString() + TestFramework.values()*.toString() == english
    }

    // the conversions of a name that the generator and the commands use
    private static Map<String, String> made(String name) {
        String last = name.substring(name.lastIndexOf('.') + 1)
        return [
                className    : NameUtils.getClassName(last),
                propertyName : NameUtils.getPropertyName(last),
                naturalName  : NameUtils.getNaturalName(last),
                scriptName   : NameUtils.getScriptName(NameUtils.getClassName(last)),
                packageName  : NameUtils.getPackageName(name),
                enumName     : NameUtils.getNaturalNameOfEnum('INITIAL_ITEM')
        ]
    }
}
