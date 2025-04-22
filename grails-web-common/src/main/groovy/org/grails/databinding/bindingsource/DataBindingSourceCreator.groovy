/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.databinding.bindingsource

import grails.databinding.CollectionDataBindingSource;
import grails.databinding.DataBindingSource;

import grails.web.mime.MimeType
import grails.web.mime.MimeTypeProvider

/**
 * A factory for DataBindingSource instances
 *
 * @since 2.3
 * @see DataBindingSource
 */
interface DataBindingSourceCreator extends MimeTypeProvider {

    /**
     * @return The target type of this creator
     */
    Class getTargetType()

    /**
     * Creates a DataBindingSource suitable for binding bindingSource to bindingTarget
     *
     * @param mimeType a mime type
     * @param bindingTarget the type of the target of the data binding
     * @param bindingSource the value being bound
     * @return a DataBindingSource
     * @throws DataBindingSourceCreationException if an unrecoverable error occurs creating the binding source
     */
    DataBindingSource createDataBindingSource(MimeType mimeType, Class bindingTargetType, Object bindingSource) throws DataBindingSourceCreationException

    /**
     * Creates a CollectionDataBindingSource suitable for binding bindingSource to bindingTarget
     *
     * @param mimeType a mime type
     * @param bindingTarget the type of the target of the data binding
     * @param bindingSource the value being bound
     * @return a CollectionDataBindingSource
     * @throws DataBindingSourceCreationException if an unrecoverable error occurs creating the binding source
     */
    CollectionDataBindingSource createCollectionDataBindingSource(MimeType mimeType, Class bindingTargetType, Object bindingSource) throws DataBindingSourceCreationException
}
