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
package grails.web.databinding

import groovy.transform.CompileStatic
import groovy.transform.Generated

import org.springframework.validation.BindingResult

import grails.databinding.CollectionDataBindingSource

/**
 *
 * Methods added to enable binding data (typically incoming request parameters) to objects and collections
 *
 * @author Jeff Brown
 * @author Graeme Rocher
 *
 * @since 3.0
 *
 */
@CompileStatic
trait DataBinder {

    @Generated
    BindingResult bindData(target, bindingSource, Map includeExclude) {
        bindData target, bindingSource, includeExclude, null
    }

    @Generated
    BindingResult bindData(target, bindingSource) {
        bindData target, bindingSource, Collections.EMPTY_MAP, null
    }

    @Generated
    BindingResult bindData(target, bindingSource, String filter) {
        bindData target, bindingSource, Collections.EMPTY_MAP, filter
    }

    @Generated
    BindingResult bindData(target, bindingSource, List excludes) {
        bindData target, bindingSource, [exclude: excludes], null
    }

    @Generated
    BindingResult bindData(target, bindingSource, List excludes, String filter) {
        bindData target, bindingSource, [exclude: excludes], filter
    }

    @Generated
    BindingResult bindData(target, bindingSource, Map includeExclude, String filter) {
        List includeList = convertToListIfCharSequence(includeExclude?.include)
        List excludeList = convertToListIfCharSequence(includeExclude?.exclude)
        DataBindingUtils.bindObjectToInstance target, bindingSource, includeList, excludeList, filter
    }

    @Generated
    void bindData(Class targetType, Collection collectionToPopulate, CollectionDataBindingSource collectionBindingSource) {
        DataBindingUtils.bindToCollection targetType, collectionToPopulate, collectionBindingSource
    }

    private List convertToListIfCharSequence(value) {
        List result
        if(value instanceof CharSequence) {
            result = []
            result << (value instanceof String ? value : value.toString())
        } else if(value instanceof List) {
            result = (List)value
        }
        result
    }
}
