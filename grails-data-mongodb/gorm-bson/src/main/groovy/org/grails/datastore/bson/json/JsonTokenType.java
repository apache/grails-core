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
 * Fork of {@link org.bson.json.JsonTokenType}
 */
enum JsonTokenType {
    /**
     * An invalid token.
     */
    INVALID,

    /**
     * A begin array token (a '[').
     */
    BEGIN_ARRAY,

    /**
     * A begin object token (a '{').
     */
    BEGIN_OBJECT,

    /**
     * An end array token (a ']').
     */
    END_ARRAY,

    /**
     * A left parenthesis (a '(').
     */
    LEFT_PAREN,

    /**
     * A right parenthesis (a ')').
     */
    RIGHT_PAREN,

    /**
     * An end object token (a '}').
     */
    END_OBJECT,

    /**
     * A colon token (a ':').
     */
    COLON,

    /**
     * A comma token (a ',').
     */
    COMMA,

    /**
     * A Double token.
     */
    DOUBLE,

    /**
     * An Int32 token.
     */
    INT32,

    /**
     * And Int64 token.
     */
    INT64,

    /**
     * A regular expression token.
     */
    REGULAR_EXPRESSION,

    /**
     * A string token.
     */
    STRING,

    /**
     * An unquoted string token.
     */
    UNQUOTED_STRING,

    /**
     * An end of file token.
     */
    END_OF_FILE
}
