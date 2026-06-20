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

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

import org.grails.observation.GrailsObservationKeyNames;

/**
 * {@link ObservationDocumentation} for binding a request's data onto a command or domain object.
 *
 * @author Apache Grails
 * @since 8.0.0
 */
public enum DataBindingObservationDocumentation implements ObservationDocumentation {

    /**
     * Binding of a {@code DataBindingSource} onto a target object.
     */
    DATABINDING {
        @Override
        public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
            return DefaultDataBindingObservationConvention.class;
        }

        @Override
        public KeyName[] getLowCardinalityKeyNames() {
            return new KeyName[] {
                DataBindingLowCardinalityKeyNames.TARGET,
                GrailsObservationKeyNames.ERROR,
            };
        }
    };

    public enum DataBindingLowCardinalityKeyNames implements KeyName {

        /** Simple class name of the bound object. */
        TARGET {
            @Override
            public String asString() {
                return "grails.databinding.target";
            }
        }
    }
}
