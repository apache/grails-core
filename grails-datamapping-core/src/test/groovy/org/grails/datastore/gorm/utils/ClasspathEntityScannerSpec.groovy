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

import org.grails.datastore.jakartafixture.JakartaTestEntity

class ClasspathEntityScannerSpec extends Specification {

    void "test classpath entity scanner finds a class annotated with grails.gorm.annotation.Entity"() {
        when: "the classpath is scanned"
        def scanner = new ClasspathEntityScanner()
        def results = scanner.scan(ClasspathEntityScannerSpec.package)

        then: "the results are correct"
        results.size() == 1
        results.first() == TestEntity
    }

    void "test classpath entity scanner finds a class annotated with jakarta.persistence.Entity"() {
        when: "the classpath is scanned"
        def scanner = new ClasspathEntityScanner()
        def results = scanner.scan(JakartaTestEntity.package)

        then: "the jakarta annotated entity is found"
        results.contains(JakartaTestEntity)
    }

    void "test classpath entity scanner ignores non-entity classes in the scanned package"() {
        when: "the classpath is scanned"
        def scanner = new ClasspathEntityScanner()
        def results = scanner.scan(ClasspathEntityScannerSpec.package)

        then: "only the annotated entity is returned"
        !results.contains(NotAnEntity)
    }

    void "test classpath entity scanner returns no results when no packages are given"() {
        when: "the scanner is invoked with no packages"
        def scanner = new ClasspathEntityScanner()
        def results = scanner.scan()

        then: "the result is empty"
        results.length == 0
    }

    void "test classpath entity scanner de-duplicates results when the same package is scanned twice"() {
        when: "the same package is passed in more than once"
        def scanner = new ClasspathEntityScanner()
        def results = scanner.scan(ClasspathEntityScannerSpec.package, ClasspathEntityScannerSpec.package)

        then: "the entity is only returned once"
        results.count { it == TestEntity } == 1
    }

    void "test classpath entity scanner combines results from multiple packages"() {
        when: "two distinct packages are scanned together"
        def scanner = new ClasspathEntityScanner()
        def results = scanner.scan(ClasspathEntityScannerSpec.package, JakartaTestEntity.package)

        then: "entities from both packages are returned"
        results.contains(TestEntity)
        results.contains(JakartaTestEntity)
    }

    void "test classpath entity scanner does not scan a package that is too generic"() {
        when: "a top-level package on the ignore list is scanned"
        def scanner = new ClasspathEntityScanner()
        def results = scanner.scan(com.IgnoredPackageEntity.package)

        then: "no entities are returned for that package"
        results.length == 0
    }
}

@Entity
class TestEntity {
    String name
}

class NotAnEntity {
    String name
}
