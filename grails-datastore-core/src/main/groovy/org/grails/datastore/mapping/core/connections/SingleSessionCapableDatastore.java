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

package org.grails.datastore.mapping.core.connections;

/**
 * Marker for a {@link MultipleConnectionSourceCapableDatastore} that manages a single, unified
 * session across all of its connection sources rather than an independently-flushed session per
 * connection.
 *
 * <p>This is not part of the public multi-connection contract. It exists solely so the GORM
 * registry can keep an entity's unqualified (no explicit connection) operations on such a
 * datastore itself, rather than routing them to a per-connection child whose session it never
 * manages independently. Production {@link MultipleConnectionSourceCapableDatastore}
 * implementations should not implement this.</p>
 */
public interface SingleSessionCapableDatastore extends MultipleConnectionSourceCapableDatastore {
}
