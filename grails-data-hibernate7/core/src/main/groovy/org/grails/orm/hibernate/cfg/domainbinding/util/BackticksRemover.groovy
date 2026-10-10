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
package org.grails.orm.hibernate.cfg.domainbinding.util

import groovy.transform.CompileStatic

import java.util.function.Function

/**
 * Removes matching leading and trailing backticks from mapping names.
 * Used to strip the Groovy backtick-quoting convention from property and column names
 * before passing them to the Hibernate mapping layer.
 *
 * @since 8.0
 */
@CompileStatic
class BackticksRemover implements Function<String, String> {

    static final String BACKTICK = '`'

    @Override
    String apply(String string) {
        if (string == null) {
            return string
        }
        String trimmed = string.trim()
        if (trimmed.length() >= 2 && trimmed.startsWith(BACKTICK) && trimmed.endsWith(BACKTICK)) {
            return trimmed.substring(1, trimmed.length() - 1)
        }
        return string
    }

}
