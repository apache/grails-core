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
package org.grails.forge.api;

import io.micronaut.http.HttpParameters;
import org.grails.forge.application.ApplicationType;
import io.swagger.v3.oas.annotations.Hidden;

import java.util.Locale;
import java.util.Objects;

/**
 * The server URL.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Hidden
public class RequestInfo {

    public static final RequestInfo LOCAL = new RequestInfo("http://localhost:8080", "/", null, Locale.ENGLISH, "");

    private final String serverURL;
    private final String currentURL;
    private final String path;
    private final HttpParameters parameters;
    private final Locale locale;
    private final String userAgent;

    /**
     * Default constructor.
     *
     * @param serverURL  The URL
     * @param path       The request path
     * @param parameters The request parameters
     * @param locale     The locale
     * @param userAgent  The browser user-agent
     */
    public RequestInfo(String serverURL, String path, HttpParameters parameters, Locale locale, String userAgent) {
        this.serverURL = Objects.requireNonNull(serverURL, "URL cannot be null");
        this.locale = locale;
        this.path = path;
        this.parameters = parameters;
        this.userAgent = userAgent;
        this.currentURL = serverURL + Objects.requireNonNull(path, "Path cannot be null");
    }

    /**
     * @return The server URL
     */
    public String getServerURL() {
        return serverURL;
    }

    /**
     * @return The current URL
     */
    public String getCurrentURL() {
        return currentURL;
    }

    /**
     * @return The self link
     */
    public LinkDTO self() {
        return new LinkDTO(getCurrentURL(), false);
    }

    /**
     * @param rel  The relationship
     * @param type The type
     * @return A new link
     */
    public LinkDTO link(Relationship rel, ApplicationType type) {
        return new LinkDTO(getServerURL() + "/" + rel + "/" + type.getName() + "/{name}");
    }

    /**
     * @param type The type
     * @return A new link
     */
    public LinkDTO link(ApplicationType type) {
        return new LinkDTO(getServerURL() + "/application-types/" + type.getName(), false);
    }

    public Locale getLocale() {
        return this.locale;
    }

    public LinkDTO link(String uri) {
        return new LinkDTO(getServerURL() + uri, false);
    }

    public String getUserAgent() {
        return userAgent;
    }

    /**
     * @return request path
     */
    public String getPath() {
        return path;
    }

    /**
     * @return request parameters
     */
    public HttpParameters getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "RequestInfo{" +
                "serverURL='" + serverURL + '\'' +
                ", currentURL='" + currentURL + '\'' +
                ", path='" + path + '\'' +
                ", parameters=" + parameters +
                ", locale=" + locale +
                ", userAgent='" + userAgent + '\'' +
                '}';
    }
}
