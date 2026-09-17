/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.bson.json

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * A JSON token.
 */
@CompileStatic
@PackageScope
class JsonToken {

    @PackageScope static final String BOOLEAN_TRUE = 'true'
    @PackageScope static final String BOOLEAN_FALSE = 'false'
    @PackageScope static final String NULL = 'null'
    @PackageScope static final char FORWARD_SLASH = '/'
    @PackageScope static final char BACK_SLASH = '\\'
    @PackageScope static final char MINUS = '-'
    @PackageScope static final char OPEN_BRACKET = '['
    @PackageScope static final char CLOSE_BRACKET = ']'
    @PackageScope static final char OPEN_PARENS = '('
    @PackageScope static final char CLOSE_PARENS = ')'
    @PackageScope static final char OPEN_BRACE = '{'
    @PackageScope static final char CLOSE_BRACE = '}'
    @PackageScope static final char COLON = ':'
    @PackageScope static final char COMMA = ','
    @PackageScope static final char SPACE = ' '
    @PackageScope static final char NEW_LINE = '\n'
    @PackageScope static final char QUOTE = '"'

    private final Object value
    private final JsonTokenType type

    JsonToken(final JsonTokenType type, final Object value) {
        this.value = value
        this.type = type
    }

    Object getValue() {
        return value
    }

    def <T> T getValue(final Class<T> clazz) {
        if (Long == clazz) {
            if (value instanceof Integer) {
                return clazz.cast(((Integer) value).longValue())
            } else if (value instanceof String) {
                return clazz.cast(Long.valueOf((String) value))
            }
        }

        try {
            return clazz.cast(value)
        } catch (ClassCastException e) {
            throw new IllegalStateException(e)
        }
    }

    JsonTokenType getType() {
        return type
    }

}
