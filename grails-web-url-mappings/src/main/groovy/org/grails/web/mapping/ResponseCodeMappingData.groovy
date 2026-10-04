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
package org.grails.web.mapping

import groovy.transform.CompileStatic

import grails.web.mapping.UrlMappingData

/**
 * A mapping data for response codes (numbers)
 *
 * @author mike
 * @since 1.0-RC1
 */
@CompileStatic
class ResponseCodeMappingData implements UrlMappingData {

    private final int responseCode
    private final String responseCodeAsString

    ResponseCodeMappingData(String responseCode) {
        this.responseCode = Integer.parseInt(responseCode)
        this.responseCodeAsString = responseCode
    }

    String[] getTokens() {
        return [responseCodeAsString] as String[]
    }

    String[] getLogicalUrls() {
        return [responseCodeAsString] as String[]
    }

    String getUrlPattern() {
        return responseCodeAsString
    }

    boolean isOptional(int index) {
        return false
    }

    @Override
    UrlMappingData createRelative(String path) {
        throw new UnsupportedOperationException('You cannot create relative UrlMappings for response codes')
    }

    @Override
    boolean hasOptionalExtension() {
        return false
    }

    @Override
    boolean hasGreedyExtensionParam() {
        return false
    }

    int getResponseCode() {
        return responseCode
    }

}
