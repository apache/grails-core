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

/**
 * @deprecated Use {@link HibernateQueryArgument} instead.
 */
@Deprecated(since = "8.0", forRemoval = true)
public interface HibernateQueryConstants {

    String ARGUMENT_FETCH_SIZE = HibernateQueryArgument.FETCH_SIZE.value();
    String ARGUMENT_TIMEOUT = HibernateQueryArgument.TIMEOUT.value();
    String ARGUMENT_READ_ONLY = HibernateQueryArgument.READ_ONLY.value();
    String ARGUMENT_FLUSH_MODE = HibernateQueryArgument.FLUSH_MODE.value();
    String ARGUMENT_MAX = HibernateQueryArgument.MAX.value();
    String ARGUMENT_OFFSET = HibernateQueryArgument.OFFSET.value();
    String ARGUMENT_ORDER = HibernateQueryArgument.ORDER.value();
    String ARGUMENT_SORT = HibernateQueryArgument.SORT.value();
    String ORDER_DESC = HibernateQueryArgument.ORDER_DESC.value();
    String ORDER_ASC = HibernateQueryArgument.ORDER_ASC.value();
    String ARGUMENT_FETCH = HibernateQueryArgument.FETCH.value();
    String ARGUMENT_IGNORE_CASE = HibernateQueryArgument.IGNORE_CASE.value();
    String ARGUMENT_CACHE = HibernateQueryArgument.CACHE.value();
    String ARGUMENT_LOCK = HibernateQueryArgument.LOCK.value();
    String CONFIG_PROPERTY_CACHE_QUERIES = HibernateQueryArgument.CONFIG_CACHE_QUERIES.value();
    String CONFIG_PROPERTY_OSIV_READONLY = HibernateQueryArgument.CONFIG_OSIV_READONLY.value();
    String CONFIG_PROPERTY_PASS_READONLY_TO_HIBERNATE = HibernateQueryArgument.CONFIG_PASS_READONLY.value();
}
