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

package org.grails.plugins.domain.support;

import java.util.LinkedHashMap;
import java.util.Map;

import groovy.lang.Closure;

import grails.gorm.validation.ConstrainedProperty;
import grails.validation.Constrained;
import grails.validation.ConstrainedDelegate;
import grails.validation.ConstraintsEvaluator;

public class ConstraintEvaluatorAdapter implements ConstraintsEvaluator {

    private final org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator constraintsEvaluator;

    public ConstraintEvaluatorAdapter(org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator constraintsEvaluator) {
        this.constraintsEvaluator = constraintsEvaluator;
    }

    @Override
    public Map<String, Object> getDefaultConstraints() {
        return constraintsEvaluator.getDefaultConstraints();
    }

    @Override
    public Map<String, Constrained> evaluate(Class cls) {
        final Map<String, ConstrainedProperty> result = constraintsEvaluator.evaluate(cls);
        return adapt(result);
    }

    @Override
    public Map<String, Constrained> evaluate(Class cls, boolean defaultNullable) {
        final Map<String, ConstrainedProperty> result = constraintsEvaluator.evaluate(cls, defaultNullable);
        return adapt(result);
    }

    @Override
    public Map<String, Constrained> evaluate(Class<?> cls, boolean defaultNullable, boolean useOnlyAdHocConstraints, Closure... adHocConstraintsClosures) {
        final Map<String, ConstrainedProperty> result = constraintsEvaluator.evaluate(cls, defaultNullable, useOnlyAdHocConstraints);
        return adapt(result);
    }

    private Map<String, Constrained> adapt(Map<String, ConstrainedProperty> result) {
        Map<String, Constrained> adapted = new LinkedHashMap<>(result.size());
        for (Map.Entry<String, ConstrainedProperty> entry : result.entrySet()) {
            adapted.put(entry.getKey(), new ConstrainedDelegate(entry.getValue()));
        }
        return adapted;
    }
}
