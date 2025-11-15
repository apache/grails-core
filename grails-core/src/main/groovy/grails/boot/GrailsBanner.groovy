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
package grails.boot

import groovy.transform.CompileStatic

import org.springframework.boot.Banner
import org.springframework.boot.SpringBootVersion
import org.springframework.core.SpringVersion
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource

import grails.util.BuildSettings

@CompileStatic
class GrailsBanner implements Banner {

    private static final int FALLBACK_BANNER_WIDTH = 80
    private static final int VERSIONS_MARGIN = 4
    private static final String VERSIONS_SEPARATOR = ' | '

    private final String bannerFile

    GrailsBanner(String bannerFile) {
        this.bannerFile = bannerFile
    }

    @Override
    void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {
        def bannerResource = new ClassPathResource(bannerFile)
        def asciiArt = bannerResource.exists() ? bannerResource.inputStream.text : ''
        def bannerWidth = longestLineLength(asciiArt) ?: FALLBACK_BANNER_WIDTH
        def versions = [
                (environment.getProperty('info.app.name') ?: 'application'): environment.getProperty('info.app.version') ?: 'unknown',
                'JVM': System.getProperty('java.vendor') + ' ' + System.getProperty('java.version'),
                'Grails': BuildSettings.grailsVersion,
                'Groovy': GroovySystem.version,
                'Spring Boot': SpringBootVersion.version,
                'Spring': SpringVersion.version
        ]
        out.println()
        out.println(asciiArt)
        buildVersionRows(versions, bannerWidth)
            .forEach { out.println(it) }
        out.println()
    }

    private static int longestLineLength(String text) {
        text.readLines()*.size()?.max() ?: 0
    }

    private static List buildVersionRows(Map versions, int bannerWidth) {
        def maxWidth = bannerWidth - VERSIONS_MARGIN * 2
        def rows = []
        def currentRow = new StringBuilder()
        def countInRow = 0
        versions.each {
            String value = "$it.key: $it.value"
            def proposedLength = currentRow.size() + (countInRow > 0 ? VERSIONS_SEPARATOR.size() : 0) + value.size()
            def wouldOverflow = proposedLength > maxWidth
            if (wouldOverflow) {
                rows << currentRow.center(bannerWidth)
                currentRow.length = 0
            }
            if (currentRow.size() > 0) {
                currentRow << VERSIONS_SEPARATOR
            }
            currentRow << value
            countInRow++
        }
        if (countInRow > 0) {
            rows << currentRow.center(bannerWidth)
        }
        rows
    }
}
