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
package org.grails.plugins.support

import org.grails.plugins.support.WatchPatternParser
import spock.lang.Specification

 /**
 * @author Graeme Rocher
 */
class WatchPatternParserSpec extends Specification{

    WatchPatternParser parser = new WatchPatternParser()

    void "Test concrete file parsing"() {
        when:
            def patterns = parser.getWatchPatterns(["file:./grails-app/conf/spring/resources.xml",
                                                    "file:./grails-app/conf/spring/resources.groovy"])

        then:
           patterns.size() == 2
           patterns[0].directory == null
           patterns[0].file != null
           patterns[0].extension == "xml"
           patterns[0].file.name == "resources.xml"
           patterns[0].matchesPath("./grails-app/conf/spring/resources.xml")

           patterns[1].directory == null
           patterns[1].file != null
           patterns[1].extension == "groovy"
           patterns[1].file.name == "resources.groovy"
           patterns[1].matchesPath("./grails-app/conf/spring/resources.groovy")
    }

    void "Test files with suffix in directory to watch"() {
        when:
            def patterns = parser.getWatchPatterns(["file:./grails-app/conf/**/*Filters.groovy"])

        then:
           patterns[0].directory.path == ".${File.separatorChar}grails-app${File.separatorChar}conf"
           patterns[0].extension == "Filters.groovy"
           patterns[0].file == null
           patterns[0].matchesPath("./grails-app/conf/spring/resources.groovy") == false
           patterns[0].matchesPath("./grails-app/conf/FooFilters.groovy") == true
    }

    void "Test files in directory watch"() {
        when:
            def patterns = parser.getWatchPatterns(["file:./grails-app/conf/spring/*.xml",
                                                    "file:./grails-app/conf/spring/**/*.groovy"])

        then:
           patterns.size() == 2
           patterns[0].directory.path == ".${File.separatorChar}grails-app${File.separatorChar}conf${File.separatorChar}spring"
           patterns[0].extension == "xml"
           patterns[0].file == null
           patterns[0].matchesPath("./grails-app/conf/spring/resources.xml") || patterns[0].matchesPath(".\\grails-app\\conf\\spring\\resources.xml")

           patterns[1].directory.path == ".${File.separatorChar}grails-app${File.separatorChar}conf${File.separatorChar}spring"
           patterns[1].extension == "groovy"
           patterns[1].file == null
           patterns[1].matchesPath("./grails-app/conf/spring/resources.groovy")
    }
}
