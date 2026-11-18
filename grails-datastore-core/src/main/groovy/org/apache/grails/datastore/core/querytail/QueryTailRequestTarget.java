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
package org.apache.grails.datastore.core.querytail;

/**
 * What a web framework resolved a request to, as a {@link QueryTailRequestReader} reads it: the page the statements
 * of the request are counted under, and how the path of the request reads against what matched it.
 *
 * @since 9.0
 */
public interface QueryTailRequestTarget {

    /**
     * @return the page the statements of the request are counted under: the controller and action serving it, as
     * {@code controller/action}, with the namespace of the controller before them, as
     * {@code namespace:controller/action}, when it has one
     */
    String getPage();

    /**
     * Reads the path of the request against what matched it: what it spells out is kept, and every value in it is
     * replaced by {@code ?}, so that an id, a token or a key carried in the path never reaches the capture.
     *
     * @param path the path of the request within the application, as the client sent it, starting with a slash
     * @return the path with its values replaced, or null when the path does not follow what matched the request, as
     * a path kept from before a forward may not, so that only its shape is recorded
     */
    String describePath(String path);
}
