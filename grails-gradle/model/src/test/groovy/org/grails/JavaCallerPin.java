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
package org.grails;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.regex.Pattern;

import org.grails.build.parsing.CommandLine;
import org.grails.build.parsing.DefaultCommandLine;
import org.grails.io.support.ClassPathResource;
import org.grails.io.support.FileSystemResource;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.io.support.Resource;
import org.grails.io.support.SpringIOUtils;
import org.grails.io.support.UrlResource;

/**
 * Compiled together with the Groovy sources, so the Java-facing API is pinned at compile time: a constant or a public
 * field that stops being a field, a constructor that disappears, or a checked exception that is no longer declared
 * (which makes the {@code catch} below unreachable) fails the build of this class.
 */
final class JavaCallerPin {

    static final String CLASS_EXTENSION = GrailsResourceUtils.CLASS_EXTENSION;

    static final int BUFFER_SIZE = SpringIOUtils.BUFFER_SIZE;

    static final String VERBOSE_ARGUMENT = CommandLine.VERBOSE_ARGUMENT;

    private JavaCallerPin() {
    }

    static Pattern[] mutablePatterns() {
        GrailsResourceUtils.DOMAIN_PATH_PATTERN = GrailsResourceUtils.DOMAIN_PATH_PATTERN;
        return new Pattern[] {
                GrailsResourceUtils.RESOURCE_PATH_PATTERN,
                GrailsResourceUtils.SPRING_SCRIPTS_PATH_PATTERN,
                GrailsResourceUtils.COMPILER_ROOT_PATTERNS[0]
        };
    }

    static CommandLine commandLine() {
        return new DefaultCommandLine();
    }

    static Resource[] resources() {
        return new Resource[] {new ClassPathResource("a"), new FileSystemResource("a")};
    }

    static boolean urlResource() {
        try {
            return new UrlResource("file:a") != null;
        }
        catch (MalformedURLException e) {
            return false;
        }
    }

    static boolean read(Resource resource, File in, File out) {
        try (InputStream stream = resource.getInputStream()) {
            return SpringIOUtils.copy(in, out) >= 0 && stream != null;
        }
        catch (IOException e) {
            return false;
        }
    }

}
