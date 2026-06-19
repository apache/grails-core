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
package org.grails.web.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;

import static org.grails.web.observation.GrailsObservationDocumentation.ControllerLowCardinalityKeyNames;

/**
 * Default {@link ControllerObservationConvention}.
 *
 * @author Apache Grails
 * @since 8.0.0
 */
public class DefaultControllerObservationConvention implements ControllerObservationConvention {

    private static final String NAME = "grails.controller";

    private static final String UNKNOWN = "unknown";

    private static final KeyValue ERROR_NONE = ControllerLowCardinalityKeyNames.ERROR.withValue("none");

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getContextualName(ControllerObservationContext context) {
        return NAME + " " + value(context.getController());
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ControllerObservationContext context) {
        return KeyValues.of(
                ControllerLowCardinalityKeyNames.CONTROLLER.withValue(value(context.getController())),
                ControllerLowCardinalityKeyNames.ACTION.withValue(value(context.getAction())),
                error(context));
    }

    protected KeyValue error(ControllerObservationContext context) {
        Throwable error = context.getError();
        return (error != null) ?
                ControllerLowCardinalityKeyNames.ERROR.withValue(error.getClass().getSimpleName()) :
                ERROR_NONE;
    }

    private static String value(String value) {
        return (value != null && !value.isEmpty()) ? value : UNKNOWN;
    }
}
