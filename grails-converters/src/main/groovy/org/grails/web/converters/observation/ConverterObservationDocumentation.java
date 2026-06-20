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
package org.grails.web.converters.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

import org.grails.observation.GrailsObservationKeyNames;

/**
 * {@link ObservationDocumentation} for serializing a value to the HTTP response via a Grails
 * converter ({@code render obj as JSON} / {@code as XML}).
 *
 * @author Apache Grails
 * @since 8.0.0
 */
public enum ConverterObservationDocumentation implements ObservationDocumentation {

    /**
     * Serialization of a converter's target object directly to the response.
     */
    CONVERT {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultConverterObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return new KeyName[] {
                ConverterLowCardinalityKeyNames.FORMAT,
                GrailsObservationKeyNames.ERROR,
            };
        }
    };

    public enum ConverterLowCardinalityKeyNames implements KeyName {

        /** Converter format: {@code json} or {@code xml}. */
        FORMAT {
            @Override
            public String asString() {
                return "grails.convert.format";
            }
        }
    }
}
