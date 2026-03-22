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

import jakarta.persistence.criteria.JoinType;

import org.grails.datastore.mapping.query.Query;

/**
 * A internal criterion used to represent an alias for a basic collection join.
 *
 * @author walterduquedeestrada
 */
public record HibernateAlias(String path, String alias, JoinType joinType) implements Query.Criterion, Query.QueryElement {

    public HibernateAlias(String path, String alias) {
        this(path, alias, JoinType.INNER);
    }

}
