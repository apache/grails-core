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

package functionaltests

import functionaltests.pages.OsivBookPage

import grails.plugin.geb.ContainerGebSpec
import grails.testing.mixin.integration.Integration
import spock.lang.Issue

@Integration
class OsivGspRenderingSpec extends ContainerGebSpec {

    @Issue('https://github.com/apache/grails-core/pull/15425')
    void 'OSIV keeps secondary datasource session open during GSP view rendering'() {
        when: 'visiting a GSP page that accesses lazy-loaded chapters from secondary datasource'
        to(OsivBookPage)

        then: 'the book title from secondary datasource is rendered'
        bookTitle == 'OSIV Test Book'

        and: 'the lazy-loaded chapters are accessible without LazyInitializationException'
        chapterTitles.containsAll(['Chapter One', 'Chapter Two'])
    }
}
