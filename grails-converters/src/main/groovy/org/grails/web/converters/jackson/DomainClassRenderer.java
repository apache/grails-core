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
package org.grails.web.converters.jackson;

import grails.converters.JSON;

/**
 * An object marshaller that renders domain class instances, and says how: with which {@link DomainClassRendering} the
 * {@link JSON} converter writes an instance of a domain class through a mapper that has a {@link DomainClassJacksonModule}.
 * {@code DomainClassMarshaller} is one; an application's own marshaller for domain classes can be another.
 *
 * @since 9.0
 */
public interface DomainClassRenderer {

    /**
     * @param json the converter that is writing
     * @param type the class of the domain class instance being written
     * @return how to render instances of the class in this write
     */
    DomainClassRendering getRendering(JSON json, Class<?> type);
}
