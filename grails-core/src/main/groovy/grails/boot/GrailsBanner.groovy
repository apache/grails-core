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
import groovy.transform.Immutable

import org.springframework.boot.Banner
import org.springframework.boot.SpringBootVersion
import org.springframework.core.SpringVersion
import org.springframework.core.env.Environment
import org.springframework.core.io.ClassPathResource

import grails.util.BuildSettings

@CompileStatic
class GrailsBanner implements Banner {

    private static final int FALLBACK_BANNER_WIDTH = 0

    String bannerFile = 'grails-banner.txt'
    int upperPadding = 1
    int lowerPadding = 1

    @Override
    void printBanner(Environment environment, Class<?> sourceClass, PrintStream out) {

        def bannerWidth = FALLBACK_BANNER_WIDTH

        upperPadding.times { out.println() }

        if (shouldDisplayArt(environment)) {
            def art = createBannerArt(environment)
            bannerWidth = longestLineLength(art) ?: FALLBACK_BANNER_WIDTH
            out.println(art)
        }

        if (shouldDisplayVersions(environment)) {
            createVersionsFormatter().format(createBannerVersions(environment), bannerWidth)
                    .forEach { out.println(it) }
        }

        lowerPadding.times { out.println() }
    }

    protected String createBannerArt(Environment environment) {
        def bannerResource = new ClassPathResource(bannerFile)
        bannerResource.exists() ? bannerResource.inputStream.text : ''
    }

    protected Map<String,String> createBannerVersions(Environment environment) {
        [
            (environment.getProperty('info.app.name') ?: 'application'): environment.getProperty('info.app.version') ?: 'unknown',
            'JVM': System.getProperty('java.vendor') + ' ' + System.getProperty('java.version'),
            'Grails': BuildSettings.grailsVersion,
            'Groovy': GroovySystem.version,
            'Spring Boot': SpringBootVersion.version,
            'Spring': SpringVersion.version
        ]
    }

    protected boolean shouldDisplayArt(Environment environment) {
        environment.getProperty('grails.banner.display.art', Boolean, true)
    }

    protected boolean shouldDisplayVersions(Environment environment) {
        environment.getProperty('grails.banner.display.versions', Boolean, true)
    }

    protected VersionsFormatter createVersionsFormatter() {
        new DefaultVersionFormatter()
    }

    private static int longestLineLength(String text) {
        text.readLines()*.size()?.max() ?: 0
    }

    @FunctionalInterface
    interface VersionsFormatter {

        /**
         * @param versions insertion-ordered map (e.g., LinkedHashMap) of label -> value
         * @param bannerWidth total banner width in characters
         * @return formatted lines to print
         */
        List<String> format(Map<String, ?> versions, int bannerWidth)
    }

    @CompileStatic
    class DefaultVersionFormatter implements VersionsFormatter {

        VersionsFormatOptions options = new VersionsFormatOptions()

        @Override
        List<String> format(Map<String, ?> versions, int bannerWidth) {
            def columnWidth = bannerWidth - options.margin * 2
            List<String> rows = []
            def currentRow = new StringBuilder()
            def countInRow = 0

            versions.each { k, v ->
                String item = "$k: $v"
                def proposedLength = currentRow.length() + (countInRow > 0 ? options.separator.size() : 0) + item.size()
                def wouldOverflow = (countInRow > 0 && proposedLength > columnWidth)
                def hitCountLimit = (options.maxItemsPerRow > 0 && countInRow >= options.maxItemsPerRow)

                if (wouldOverflow || hitCountLimit) {
                    rows << currentRow.center(bannerWidth)
                    currentRow.length = 0
                    countInRow = 0
                }

                if (currentRow.size() > 0) {
                    currentRow << options.separator
                }
                currentRow << item
                countInRow++
            }

            if (countInRow > 0) {
                rows << currentRow.center(bannerWidth)
            }

            return rows
        }
    }

    @Immutable
    @CompileStatic
    class VersionsFormatOptions {
        int margin = 4
        int maxItemsPerRow = 0 // 0 or negative = unlimited
        String separator = ' | '
    }
}
