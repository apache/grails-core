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
package org.grails.taglib.index;

/**
 * One tag recorded in the {@link TagLibraryIndex} at compile time.
 *
 * @param namespace the tag library namespace the tag is reachable through
 * @param tagName the tag name within that namespace
 * @param tagLibraryClassName the binary name of the tag library declaring the tag
 * @param acceptsBody whether the tag can be called with a body
 * @since 8.0.0
 */
public record TagLibraryIndexEntry(String namespace, String tagName, String tagLibraryClassName,
        boolean acceptsBody) {
}
