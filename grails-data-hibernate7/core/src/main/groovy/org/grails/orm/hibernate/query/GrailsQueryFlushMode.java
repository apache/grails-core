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

import jakarta.persistence.FlushModeType;

import org.hibernate.FlushMode;
import org.hibernate.query.QueryFlushMode;

/**
 * An enum that maps traditional GORM/Hibernate flush modes to Hibernate 7 {@link QueryFlushMode}.
 *
 * @author Graeme Rocher
 * @since 7.0.0
 */
public enum GrailsQueryFlushMode {
    AUTO(QueryFlushMode.DEFAULT),
    COMMIT(QueryFlushMode.NO_FLUSH),
    MANUAL(QueryFlushMode.NO_FLUSH),
    ALWAYS(QueryFlushMode.FLUSH),
    DEFAULT(QueryFlushMode.DEFAULT);

    private final QueryFlushMode queryFlushMode;

    GrailsQueryFlushMode(QueryFlushMode queryFlushMode) {
        this.queryFlushMode = queryFlushMode;
    }

    public QueryFlushMode getQueryFlushMode() {
        return queryFlushMode;
    }

    /**
     * Maps an object (String, FlushMode, FlushModeType, etc.) to a Hibernate 7 {@link QueryFlushMode}.
     *
     * @param object The object to map
     * @return The mapped {@link QueryFlushMode}
     */
    public static QueryFlushMode mapToHibernateQueryFlushMode(Object object) {
        if (object == null) {
            return QueryFlushMode.DEFAULT;
        }
        if (object instanceof QueryFlushMode) {
            return (QueryFlushMode) object;
        }
        if (object instanceof GrailsQueryFlushMode) {
            return ((GrailsQueryFlushMode) object).getQueryFlushMode();
        }
        if (object instanceof FlushMode) {
            FlushMode fm = (FlushMode) object;
            switch (fm) {
                case ALWAYS:
                    return QueryFlushMode.FLUSH;
                case MANUAL:
                case COMMIT:
                    return QueryFlushMode.NO_FLUSH;
                default:
                    return QueryFlushMode.DEFAULT;
            }
        }
        if (object instanceof FlushModeType) {
            FlushModeType fmt = (FlushModeType) object;
            switch (fmt) {
                case COMMIT:
                    return QueryFlushMode.NO_FLUSH;
                default:
                    return QueryFlushMode.DEFAULT;
            }
        }

        String s = object.toString().toUpperCase(Locale.ROOT);
        try {
            return GrailsQueryFlushMode.valueOf(s).getQueryFlushMode();
        } catch (IllegalArgumentException e) {
            try {
                return QueryFlushMode.valueOf(s);
            } catch (IllegalArgumentException e2) {
                return QueryFlushMode.DEFAULT;
            }
        }
    }
}
