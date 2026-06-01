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
package org.grails.forge.api;

import io.micronaut.core.util.StringUtils;
import org.grails.forge.application.OperatingSystem;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserAgentParser {

    private static final Pattern[] WINDOWS_PATTERNS = new Pattern[] { Pattern.compile("Windows"), Pattern.compile("Win ?(95|98|3.1|NT|ME|2000)") };
    private static final Pattern[] MACOS_PATTERNS = new Pattern[] { Pattern.compile("(?:Mac[ +]?|; )OS[ +]X"), Pattern.compile("Darwin"), Pattern.compile("Mac_PowerPC") };
    private static final Pattern[] LINUX_PATTERNS = new Pattern[] { Pattern.compile("Linux"), Pattern.compile("Ubuntu|Kubuntu|CentOS|Slackware|Gentoo|openSUSE|SUSE|Red Hat|Fedora|Mageia|(?:Free|Open|Net|\\b)BSD") };
    private static final Pattern[] SOLARIS_PATTERNS = new Pattern[] { Pattern.compile("SunOS") };

    public static OperatingSystem getOperatingSystem(String userAgent) {
        if (StringUtils.isNotEmpty(userAgent)) {
            if (matches(WINDOWS_PATTERNS, userAgent)) {
                return OperatingSystem.WINDOWS;
            }
            if (matches(MACOS_PATTERNS, userAgent)) {
                return OperatingSystem.MACOS;
            }
            if (matches(LINUX_PATTERNS, userAgent)) {
                return OperatingSystem.LINUX;
            }
            if (matches(SOLARIS_PATTERNS, userAgent)) {
                return OperatingSystem.SOLARIS;
            }
        }
        return null;
    }

    private static boolean matches(Pattern[] patterns, String userAgent) {
        return Arrays.stream(patterns).map(pattern -> pattern.matcher(userAgent))
                .anyMatch(Matcher::find);
    }
}
