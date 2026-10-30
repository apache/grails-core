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
package org.grails.plugins.xml;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.grails.core.lifecycle.ShutdownOperations;

/**
 * Logs, once per application and format, that a response is rendered in an XML format scheduled for removal. The
 * renderers of these formats are registered as beans, where an application's build reports no deprecation.
 *
 * @since 9.0
 */
public final class DeprecatedXmlNotice {

    private static final Log LOG = LogFactory.getLog(DeprecatedXmlNotice.class);

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    static {
        ShutdownOperations.addOperation(REPORTED::clear, true);
    }

    private DeprecatedXmlNotice() {
    }

    /**
     * @param format the format, such as {@code HAL XML}
     * @param advice what to render instead
     */
    public static void report(String format, String advice) {
        if (REPORTED.add(format)) {
            LOG.warn(format + " rendering is scheduled for removal in Grails 11. " + advice);
        }
    }
}
