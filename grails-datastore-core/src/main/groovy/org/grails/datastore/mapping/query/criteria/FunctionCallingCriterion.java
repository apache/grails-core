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
package org.grails.datastore.mapping.query.criteria;

import org.grails.datastore.mapping.query.Query;

/**
 * This criterion calls a function on the property before apply the appropriate comparison.
 * <p>
 * Example in SQL: year(date) == 2007 where the function is 'year', the property 'date' and the value is '2007'
 */
public class FunctionCallingCriterion extends Query.PropertyNameCriterion {

    private String functionName;
    private Query.PropertyCriterion propertyCriterion;
    private boolean onValue;

    public FunctionCallingCriterion(String functionName, Query.PropertyCriterion propertyCriterion) {
        super(propertyCriterion.getProperty());
        this.functionName = functionName;
        this.propertyCriterion = propertyCriterion;
    }

    public FunctionCallingCriterion(String name, String functionName, Query.PropertyCriterion propertyCriterion, boolean onValue) {
        super(name);
        this.functionName = functionName;
        this.propertyCriterion = propertyCriterion;
        this.onValue = onValue;
    }

    public String getFunctionName() {
        return functionName;
    }

    public Query.PropertyCriterion getPropertyCriterion() {
        return propertyCriterion;
    }

    /**
     * Whether the function is called on the value or on the property
     *
     */
    public boolean isOnValue() {
        return onValue;
    }
}
