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
package org.grails.plugins.support;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.springframework.util.StringUtils;

import grails.io.ResourceUtils;
import grails.util.BuildSettings;
import grails.util.GrailsStringUtils;

/**
 * Parses a Grails plugin's watchedResources property value into a list of
 *
 * @since 2.0
 * @author Graeme Rocher
 */
public class WatchPatternParser {

    public static final String WILD_CARD = "*";

    public List<WatchPattern> getWatchPatterns(List<String> patterns) {
       List<WatchPattern> watchPatterns = new ArrayList<WatchPattern>();

        for (String pattern : patterns) {
            WatchPattern watchPattern = new WatchPattern();
            watchPattern.setPattern(pattern);
            boolean isClasspath = false;
            if (pattern.startsWith(ResourceUtils.FILE_URL_PREFIX)) {
                pattern = pattern.substring(ResourceUtils.FILE_URL_PREFIX.length());
            }
            else if (pattern.startsWith(ResourceUtils.CLASSPATH_URL_PREFIX)) {
                pattern = pattern.substring(ResourceUtils.CLASSPATH_URL_PREFIX.length());
                isClasspath = true;
            }

            if (pattern.contains(WILD_CARD)) {
                String dirPath = pattern.substring(0, pattern.indexOf(WILD_CARD));
                if(!GrailsStringUtils.isBlank(dirPath)) {
                    watchPattern.setDirectory(new File(dirPath));
                }
                else if(isClasspath && BuildSettings.BASE_DIR != null) {
                    watchPattern.setDirectory(new File(BuildSettings.BASE_DIR, "src/main/resources"));
                }

                setExtension(pattern, watchPattern);
                watchPatterns.add(watchPattern);
            }
            else {
                setExtension(pattern, watchPattern);
                watchPattern.setFile(new File(pattern));
                watchPatterns.add(watchPattern);
            }
        }

       return watchPatterns;
    }

    private void setExtension(String pattern, WatchPattern watchPattern) {
        int i = pattern.lastIndexOf('*');
        if (i > -1) {
            String extension = pattern.substring(i + 1, pattern.length());
            if (extension.startsWith(".")) {
                extension = extension.substring(1);
            }
            watchPattern.setExtension(extension);
        }
        else {
            String ext = StringUtils.getFilenameExtension(pattern);
            if (ext != null) {
                watchPattern.setExtension(ext);
            }
        }
    }
}
