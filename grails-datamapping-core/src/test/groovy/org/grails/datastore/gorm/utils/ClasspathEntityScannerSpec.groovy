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

package org.grails.datastore.gorm.utils

import grails.gorm.annotation.Entity
import spock.lang.Specification

/**
 * Created by graemerocher on 18/11/16.
 */
class ClasspathEntityScannerSpec extends Specification {

    void "test classpath entity scanner"() {
        when:"the classpath is scanned"
        def scanner = new ClasspathEntityScanner()
        def results = scanner.scan(ClasspathEntityScannerSpec.package)

        then:"The results are correct"
        results.size() == 1
        results.first() == TestEntity
    }
}

@Entity
class TestEntity {
    String name
}
