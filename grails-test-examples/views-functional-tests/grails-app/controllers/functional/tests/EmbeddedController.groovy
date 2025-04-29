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

package functional.tests

import grails.gorm.transactions.Transactional

class EmbeddedController {

    static responseFormats = ['json']

    @Transactional
    def index() {
        Embedded embedded = new Embedded(name: "Foo", customClass: new CustomClass(name: "Bar"), inSameFile: new InSameFile(text: "FooBar"))
        embedded.save(flush: true)
        [embedded: embedded]
    }

    @Transactional
    def jsonapi() {
        Embedded embedded = new Embedded(name: "Foo2", customClass: new CustomClass(name: "Bar2"), inSameFile: new InSameFile(text: "FooBar2"))
        embedded.save(flush: true)
        [embedded: embedded]
    }

    @Transactional
    def embeddedWithIncludes() {
        Embedded embedded = new Embedded(name: "Foo3", customClass: new CustomClass(name: "Bar3"), inSameFile: new InSameFile(text: "FooBar3"))
        embedded.save(flush: true)
        [embedded: embedded]
    }

    @Transactional
    def embeddedWithIncludesJsonapi() {
        Embedded embedded = new Embedded(name: "Foo4", customClass: new CustomClass(name: "Bar4"), inSameFile: new InSameFile(text: "FooBar4"))
        embedded.save(flush: true)
        [embedded: embedded]
    }
}
