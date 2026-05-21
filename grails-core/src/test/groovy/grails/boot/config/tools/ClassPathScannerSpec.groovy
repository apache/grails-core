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

package grails.boot.config.tools

import grails.persistence.Entity
import spock.lang.Specification

/**
 * Created by graemerocher on 06/10/2016.
 */
class ClassPathScannerSpec extends Specification {
    void "Test classpath scanner with package names"() {
        when:"the classpath is scanned"
        ClassPathScanner scanner = new ClassPathScanner()
        def results = scanner.scan(Application, ["grails.boot.config.tools"])

        then:"the results are correct"
        results.size() == 1
        results.contains(Foo)
        Foo.classLoader == Application.classLoader
    }

    void "Test classpath scanner with application"() {
        when:"the classpath is scanned"
        ClassPathScanner scanner = new ClassPathScanner()
        def results = scanner.scan(Application)

        then:"the results are correct"
        results.size() == 1
        results.contains(Foo)
    }
}

class Application {

}

@Entity
class Foo {}