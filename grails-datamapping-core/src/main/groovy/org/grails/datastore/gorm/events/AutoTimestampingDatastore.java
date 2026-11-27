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
package org.grails.datastore.gorm.events;

/**
 * A datastore that sets the timestamp and auditor properties of the entities it saves through an
 * {@link AutoTimestampEventListener} of its own. Every datastore has its own listener, and so does every connection
 * of a datastore with several, and a listener only handles the entities saved through its own datastore. Turning
 * timestamps off on one listener therefore leaves the entities saved through any other datastore or connection
 * untouched.
 *
 * @since 9.0
 */
public interface AutoTimestampingDatastore {

    /**
     * @return the listener that sets the timestamps of the entities this datastore saves, or {@code null} when this
     * datastore sets none
     */
    AutoTimestampEventListener getAutoTimestampEventListener();

}
