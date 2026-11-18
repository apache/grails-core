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
package org.apache.grails.startup;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import grails.util.Environment;

/**
 * Settings for the page Grails serves on the application's port while the application starts,
 * bound from {@code grails.startup.progress.*}.
 *
 * <p>Both settings default to on in development mode, that is when the environment is
 * {@code development} and the application runs from its project directory, as it does under
 * {@code bootRun}, and to off otherwise.</p>
 *
 * @since 8.1
 */
@ConfigurationProperties(prefix = "grails.startup.progress")
public class StartupProgressProperties {

    static final String DEFAULT_STATUS_PATH = "/__grails/startup-progress";

    private Boolean enabled;

    private String statusPath = DEFAULT_STATUS_PATH;

    private Boolean showDetails;

    private boolean openBrowser;

    private List<String> browserCommand = new ArrayList<>();

    private final Endpoint endpoint = new Endpoint();

    /**
     * Whether Grails answers on the application's port with a progress page until the embedded web
     * server takes the port over. {@code null} means development mode decides.
     */
    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * The path, below the context path, that the progress page polls for the progress of the start. Once the
     * application is ready, a poll there is told so, so the path is the application's no longer.
     */
    public String getStatusPath() {
        return statusPath;
    }

    public void setStatusPath(String statusPath) {
        this.statusPath = statusPath;
    }

    /**
     * Whether the progress page names the bean being created, lists the slowest beans, and shows the
     * exception when startup fails, to a browser signed in with the address the application logs.
     * {@code null} means development mode decides.
     */
    public Boolean getShowDetails() {
        return showDetails;
    }

    public void setShowDetails(Boolean showDetails) {
        this.showDetails = showDetails;
    }

    /**
     * Whether to open a browser on the application when it starts: on the progress page as soon as it is
     * served, or on the application once it is ready when the page is not served. A browser is opened once
     * per address for the life of the JVM, so a restart by Spring Boot DevTools does not open another.
     */
    public boolean isOpenBrowser() {
        return openBrowser;
    }

    public void setOpenBrowser(boolean openBrowser) {
        this.openBrowser = openBrowser;
    }

    /**
     * The command that opens the browser, to which the address is added as the last argument. When empty,
     * the operating system's own is used: {@code open} on macOS, {@code xdg-open} elsewhere on Unix, and
     * {@code rundll32 url.dll,FileProtocolHandler} on Windows.
     */
    public List<String> getBrowserCommand() {
        return browserCommand;
    }

    public void setBrowserCommand(List<String> browserCommand) {
        this.browserCommand = browserCommand;
    }

    /** The startup report the application serves once it has started. */
    public Endpoint getEndpoint() {
        return endpoint;
    }

    boolean isEnabledOrDefault() {
        return enabled != null ? enabled : Environment.isDevelopmentMode();
    }

    boolean isShowDetailsOrDefault() {
        return showDetails != null ? showDetails : Environment.isDevelopmentMode();
    }

    /**
     * Where, and whether, the application serves a report of how it started once it is running: the time
     * each stage took and the slowest beans, as a page or, to a client asking for JSON, as data.
     */
    public static class Endpoint {

        static final String DEFAULT_PATH = "/__grails/startup";

        private Boolean enabled;

        private String path = DEFAULT_PATH;

        /**
         * Whether the application serves the startup report. {@code null} means development mode decides.
         */
        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        /** The path of the startup report, below the context path. */
        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        boolean isEnabledOrDefault() {
            return enabled != null ? enabled : Environment.isDevelopmentMode();
        }
    }
}
