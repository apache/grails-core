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
package grails.web.databinding.observation;

import io.micrometer.common.KeyValues;
import io.micrometer.common.docs.KeyName;

import org.grails.observation.GrailsObservationConvention;
import org.grails.observation.GrailsObservationKeyNames;

import static grails.web.databinding.observation.DataBindingObservationDocumentation.DataBindingLowCardinalityKeyNames;

/**
 * Default {@link DataBindingObservationConvention}.
 *
 * @since 8.0
 */
public class DefaultDataBindingObservationConvention extends GrailsObservationConvention<DataBindingObservationContext>
        implements DataBindingObservationConvention {

    private static final String NAME = "grails.databinding";

    public DefaultDataBindingObservationConvention() {
        super(DataBindingObservationContext.class);
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getContextualName(DataBindingObservationContext context) {
        return NAME + " " + orElse(context.getTarget(), UNKNOWN);
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(DataBindingObservationContext context) {
        return KeyValues.of(
                DataBindingLowCardinalityKeyNames.TARGET.withValue(orElse(context.getTarget(), UNKNOWN)),
                error(context));
    }

    @Override
    protected KeyName errorKeyName() {
        return GrailsObservationKeyNames.ERROR;
    }
}
