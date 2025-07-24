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
package org.grails.web.databinding.bindingsource

import grails.databinding.CollectionDataBindingSource
import grails.databinding.DataBindingSource
import grails.web.mime.MimeType
import org.grails.databinding.bindingsource.DataBindingSourceCreator

/**
 * Responsible for locating DataBindingSourceCreator instances and
 * using them to create DataBindingSource instances
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 *
 * @since 2.3
 * @see DataBindingSourceCreator
 */
interface DataBindingSourceRegistry {
    String BEAN_NAME = 'dataBindingSourceRegistry'

    /**
     * Adds a new {@link DataBindingSourceCreator} to the registry
     * @param creator The {@link DataBindingSourceCreator}
     */
    void addDataBindingSourceCreator(DataBindingSourceCreator creator )

    /**
     * Locates a {@link DataBindingSource} for the given MimeType and binding target
     * @param mimeType The MimeType
     * @param bindingTarget The type of the binding target
     * @param bindingSource The binding source
     * @return The {@link DataBindingSource}
     */
    DataBindingSource createDataBindingSource(MimeType mimeType, Class bindingTargetType, Object bindingSource)

    /**
     * Locates a {@link CollectionDataBindingSource} for the given MimeType and binding target
     * @param mimeType The MimeType
     * @param bindingTarget The type of the binding target
     * @param bindingSource The binding source
     * @return The {@link CollectionDataBindingSource}
     */
    CollectionDataBindingSource createCollectionDataBindingSource(MimeType mimeType, Class bindingTargetType, Object bindingSource)
}
