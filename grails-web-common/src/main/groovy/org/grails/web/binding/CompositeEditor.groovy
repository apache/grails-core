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
package org.grails.web.binding

import java.beans.PropertyEditor
import java.beans.PropertyEditorSupport

import groovy.transform.CompileStatic

/**
 * @author Graeme Rocher
 * @since 2.0
 */
@CompileStatic
class CompositeEditor extends PropertyEditorSupport {

    List<PropertyEditor> propertyEditors = new ArrayList<>()

    CompositeEditor(PropertyEditor... editors) {
        propertyEditors.addAll(Arrays.asList(editors))
    }

    @Override
    void setAsText(String s) throws IllegalArgumentException {
        IllegalArgumentException first = null
        boolean converted = false
        for (PropertyEditor propertyEditor in propertyEditors) {
            try {
                propertyEditor.setAsText(s)
                converted = true
                break
            } catch (IllegalArgumentException e) {
                if (first == null) first = e
            }
        }

        if (!converted && first != null) {
            throw first
        }
    }

    List<PropertyEditor> getPropertyEditors() {
        return propertyEditors
    }

}
