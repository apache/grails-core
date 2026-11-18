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

package org.grails.orm.hibernate.query;

import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The HQL keywords the reader of a query looks for, and which of them start a clause: a token after the root
 * entity that starts a clause is the clause, not an alias of the entity.
 *
 * @since 8.0.1
 */
enum HqlKeyword {

    SELECT("select", false),
    FROM("from", false),
    DISTINCT("distinct", false),
    ALL("all", false),
    AS("as", false),
    NEW("new", false),
    WHERE("where", true),
    JOIN("join", true),
    LEFT("left", true),
    RIGHT("right", true),
    INNER("inner", true),
    OUTER("outer", true),
    GROUP("group", true),
    ORDER("order", true),
    HAVING("having", true);

    private static final Map<String, HqlKeyword> BY_TEXT = Stream.of(values())
            .collect(Collectors.toUnmodifiableMap(HqlKeyword::text, Function.identity()));

    private final String text;
    private final boolean startsClause;

    HqlKeyword(String text, boolean startsClause) {
        this.text = text;
        this.startsClause = startsClause;
    }

    /** The keyword in lower case, as the reader matches it against a lowercased query. */
    String text() {
        return text;
    }

    /** Whether a token, in any case, is a keyword that starts a clause. */
    static boolean startsClause(String token) {
        HqlKeyword keyword = BY_TEXT.get(token.toLowerCase(Locale.ROOT));
        return keyword != null && keyword.startsClause;
    }
}
