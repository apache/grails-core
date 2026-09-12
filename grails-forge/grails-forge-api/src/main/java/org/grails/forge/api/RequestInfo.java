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

import org.grails.forge.application.ApplicationType;

import java.util.Locale;
import java.util.Objects;

public class RequestInfo {

    public static final RequestInfo LOCAL = new RequestInfo("http://localhost:8080", "/", Locale.ENGLISH, "");

    private final String serverURL;
    private final String currentURL;
    private final String path;
    private final Locale locale;
    private final String userAgent;

    public RequestInfo(String serverURL, String path, Locale locale, String userAgent) {
        this.serverURL = Objects.requireNonNull(serverURL, "URL cannot be null");
        this.locale = locale;
        this.path = path;
        this.userAgent = userAgent;
        this.currentURL = serverURL + Objects.requireNonNull(path, "Path cannot be null");
    }

    public String getServerURL() {
        return serverURL;
    }

    public String getCurrentURL() {
        return currentURL;
    }

    public LinkDTO self() {
        return new LinkDTO(getCurrentURL(), false);
    }

    public LinkDTO link(Relationship rel, ApplicationType type) {
        return new LinkDTO(getServerURL() + "/" + rel + "/" + type.getName() + "/{name}");
    }

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

    public String getPath() {
        return path;
    }
}
