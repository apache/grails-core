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
package org.grails.web.databinding;

import grails.beans.util.LazyMetaPropertyMap;
import grails.web.databinding.DataBindingUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extends the default implementation and does data binding.
 *
 * @author Graeme Rocher
 * @since 1.1
 */
public class DataBindingLazyMetaPropertyMap extends LazyMetaPropertyMap {

    /**
     * Constructs the map
     *
     * @param o The object to inspect
     */
    public DataBindingLazyMetaPropertyMap(Object o) {
        super(o);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Object put(Object propertyName, Object propertyValue) {
        if (propertyName instanceof List) {
            DataBindingUtils.bindObjectToInstance(getInstance(), propertyValue, (List) propertyName, null, null);
        } else {
            final Map bindingSource;
            if (propertyValue instanceof Map) {
                bindingSource = (Map) propertyValue;
            } else {
                bindingSource = new HashMap();
                bindingSource.put(propertyName, propertyValue);
            }
            List propertyNames = new ArrayList();
            propertyNames.add(propertyName);
            DataBindingUtils.bindObjectToInstance(getInstance(), bindingSource, propertyNames, null, null);
        }
        return null;
    }
}
