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
package org.apache.grails.gradle.model.cli

import spock.lang.Specification
import spock.lang.Unroll

/**
 * The descriptor a build writes for a command is the same bytes whatever the language of the computer
 * that builds: the name a command has by its class is that of no language.
 */
class CommandDescriptorLocaleSpec extends Specification {

    Locale locale

    void setup() {
        locale = Locale.getDefault()
    }

    void cleanup() {
        Locale.setDefault(locale)
    }

    @Unroll
    def "the name #className has by convention, and its descriptor, are the same with the locale #tag"() {
        given:
        Locale.setDefault(Locale.forLanguageTag(tag))

        expect:
        CommandDescriptor.conventionName(className) == name
        CommandDescriptor.describe(className, CommandDescriptor.conventionName(className), 'Migrates the index') ==
                CommandDescriptor.toJson(className, name, 'Migrates the index', CommandDescriptor.TARGET_APPLICATION)

        where:
        className                              | tag     || name
        'example.MigrateIndexCommand'          | 'en-US' || 'migrate-index'
        'example.MigrateIndexCommand'          | 'tr-TR' || 'migrate-index'
        'example.MigrateIndexCommand'          | 'az-AZ' || 'migrate-index'
        'example.MigrateIndexCommand'          | 'lt-LT' || 'migrate-index'
        'example.ImportInitialIdsCommand'      | 'tr-TR' || 'import-initial-ids'
        'example.ImportInitialIdsCommand'      | 'de-DE' || 'import-initial-ids'
    }
}
