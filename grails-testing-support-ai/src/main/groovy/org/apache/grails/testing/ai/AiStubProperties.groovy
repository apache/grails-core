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
package org.apache.grails.testing.ai

import groovy.transform.CompileStatic

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Settings of the stubs that stand in for the configured Spring AI provider while this module is on the
 * classpath, under {@code grails.ai.stub}.
 *
 * @since 8.1
 */
@CompileStatic
@ConfigurationProperties(PREFIX)
class AiStubProperties {

    public static final String PREFIX = 'grails.ai.stub'

    /**
     * Whether a scripted chat model and a deterministic embedding model stand in for the configured
     * provider, which then needs no credentials. On by default in the test environment and off anywhere
     * else; set to false to run tests against the real provider, or to true to use the stubs elsewhere.
     */
    boolean enabled = true

    /**
     * The number of dimensions of the vectors the stub embedding model produces.
     */
    int embeddingDimensions = StubEmbeddingModel.DEFAULT_DIMENSIONS

}
