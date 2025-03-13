/*
 * Copyright (c) 2008-2014 MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.bson.json;

/**
 * A JSON token.
 */
class JsonToken {

    static final String BOOLEAN_TRUE = "true";
    static final String BOOLEAN_FALSE = "false";
    static final String NULL = "null";
    static final char FORWARD_SLASH = '/';
    static final char BACK_SLASH = '\\';
    static final char MINUS = '-';
    static final char OPEN_BRACKET = '[';
    static final char CLOSE_BRACKET = ']';
    static final char OPEN_PARENS = '(';
    static final char CLOSE_PARENS = ')';
    static final char OPEN_BRACE = '{';
    static final char CLOSE_BRACE = '}';
    static final char COLON = ':';
    static final char COMMA = ',';
    static final char SPACE = ' ';
    static final char NEW_LINE = '\n';
    static final char QUOTE = '"';

    private final Object value;
    private final JsonTokenType type;

    public JsonToken(final JsonTokenType type, final Object value) {
        this.value = value;
        this.type = type;
    }

    public Object getValue() {
        return value;
    }

    public <T> T getValue(final Class<T> clazz) {
        if (Long.class == clazz) {
            if (value instanceof Integer) {
                return clazz.cast(((Integer) value).longValue());
            } else if (value instanceof String) {
                return clazz.cast(Long.valueOf((String) value));
            }
        }

        try {
            return clazz.cast(value);
        } catch (ClassCastException e) {
            throw new IllegalStateException(e);
        }
    }

    public JsonTokenType getType() {
        return type;
    }
}
