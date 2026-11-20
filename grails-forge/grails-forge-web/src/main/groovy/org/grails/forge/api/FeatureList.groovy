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
package org.grails.forge.api

import groovy.transform.CompileStatic

/**
 * Models a list of features.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@CompileStatic
class FeatureList extends Linkable {

    private List<FeatureDTO> features

    /**
     * Constructor.
     */
    FeatureList() {

    }

    /**
     *
     * @param features A list of features.
     */
    FeatureList(List<FeatureDTO> features) {
        this.features = features
    }

    /**
     * @return A list of features.
     */
    List<FeatureDTO> getFeatures() {
        return features
    }

    /**
     *
     * @param features a list of features.
     */
    void setFeatures(List<FeatureDTO> features) {
        this.features = features
    }
}
