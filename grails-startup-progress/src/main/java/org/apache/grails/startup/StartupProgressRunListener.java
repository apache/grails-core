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

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.DefaultLifecycleProcessor;
import org.springframework.core.SpringProperties;
import org.springframework.util.StringUtils;

import grails.util.Environment;

/**
 * Serves a progress page on the application's port from the moment the application context is
 * prepared until the application is ready, so a browser pointed at a starting application shows how
 * far the start has got instead of failing to connect, and why it failed if it does.
 *
 * <p>The page polls a status path. Until the web server starts, a small server on the JDK's built-in
 * HTTP server answers it; that server is stopped in the lifecycle phase just before the web server's,
 * and from then on a filter inside the application answers the same path, and serves the page to
 * browsers loading one, through the plugin startup hooks and {@code BootStrap} classes Grails runs after
 * the web server starts. Once the application is ready the filter answers the poll with that, and the
 * page reloads.</p>
 *
 * <p>The page is only served for a servlet application starting its own embedded web server on a fixed
 * port without SSL, not when the JVM is to take a CRaC checkpoint on refresh, which an open port would fail,
 * and only when {@code grails.startup.progress.enabled} allows it, which by default it does in development
 * mode. If the port cannot be bound the application starts exactly as it would without the page.</p>
 *
 * <p>When {@code grails.startup.progress.openBrowser} is set, a browser is opened on the page as soon as it
 * is served, or on the application once it is ready when the page is not served.</p>
 *
 * <p>When {@code grails.startup.progress.endpoint.enabled} allows it, which by default it does in
 * development mode, the application also serves a report of its start for as long as it runs, at
 * {@code grails.startup.progress.endpoint.path}. The report does not need the progress page, so it is
 * served wherever the application starts its own embedded web server.</p>
 *
 * @since 8.0.1
 */
public class StartupProgressRunListener implements SpringApplicationRunListener {

    private static final Logger LOG = LoggerFactory.getLogger(StartupProgressRunListener.class);

    private static final String PROPERTIES_PREFIX = "grails.startup.progress";

    private static final int DEFAULT_PORT = 8080;

    /** How long a failed start waits for a watching page to be told why, before it carries on failing. */
    private static final long FAILURE_DELIVERY_TIMEOUT_MILLIS = 2000;

    private final SpringApplication application;

    /** When the run of the application began, which the times in the report are measured from. */
    private final long runStartNanos = System.nanoTime();

    private StartupProgress progress;

    private StartupAccess access;

    /** The full path of the startup report, while it is served. */
    private String reportPath;

    private StartupProgressServer server;

    private InetAddress address;

    private String contextPath = "";

    private boolean ssl;

    /** The command that opens a browser, while one is still to be opened for this start. */
    private List<String> browserCommand;

    public StartupProgressRunListener(SpringApplication application, String[] args) {
        this.application = application;
    }

    @Override
    public void contextPrepared(ConfigurableApplicationContext context) {
        if (application.getWebApplicationType() != WebApplicationType.SERVLET ||
                !(context instanceof WebServerApplicationContext) ||
                !StartupProgressFilter.startsEmbeddedServer(context)) {
            return;
        }
        Binder binder = Binder.get(context.getEnvironment());
        StartupProgressProperties properties;
        try {
            properties = binder.bindOrCreate(PROPERTIES_PREFIX, StartupProgressProperties.class);
        }
        catch (RuntimeException ex) {
            // nothing else reads these settings, so a mistake in them is said here or not at all
            LOG.warn("Not serving startup progress: the {} settings could not be read: {}", PROPERTIES_PREFIX, ex.getMessage());
            return;
        }
        int port;
        try {
            port = binder.bind("server.port", Integer.class).orElse(DEFAULT_PORT);
            address = binder.bind("server.address", InetAddress.class).orElse(null);
            contextPath = contextPath(binder.bind("server.servlet.context-path", String.class).orElse(""));
            ssl = isSslEnabled(binder);
        }
        catch (RuntimeException ex) {
            // the web server reports a bad server.* setting far more usefully than this page could
            LOG.debug("Not serving startup progress: the server settings could not be read", ex);
            return;
        }
        if (properties.isOpenBrowser()) {
            browserCommand = properties.getBrowserCommand();
        }
        boolean pageEnabled = properties.isEnabledOrDefault();
        boolean reportEnabled = properties.getEndpoint().isEnabledOrDefault();
        if (!pageEnabled && !reportEnabled) {
            return;
        }

        String applicationName = applicationName(binder);
        StartupProgress startupProgress = new StartupProgress(applicationName, Environment.getGrailsVersion(), runStartNanos);
        StartupAccess startupAccess = new StartupAccess(properties.isShowDetailsOrDefault(), port);
        String statusPath = path(properties.getStatusPath(), StartupProgressProperties.DEFAULT_STATUS_PATH, "startup progress status");
        StartupProgressPage page = new StartupProgressPage(applicationName, statusPath, Environment.getGrailsVersion());
        reportPath = reportEnabled ? path(properties.getEndpoint().getPath(), StartupProgressProperties.Endpoint.DEFAULT_PATH, "startup report") : null;
        if (statusPath.equals(reportPath)) {
            // a page polling there would take the report for the application answering, and reload without end
            LOG.warn("Not serving the startup report at {}: it is the path the startup progress page polls", reportPath);
            reportPath = null;
            reportEnabled = false;
        }
        StartupProgressResponder responder = new StartupProgressResponder(startupProgress, page, startupAccess, statusPath, reportPath);
        boolean servingPage = pageEnabled && servePage(responder, port);
        if (!servingPage && !reportEnabled) {
            reportPath = null;
            return;
        }
        progress = startupProgress;
        access = startupAccess;

        context.setApplicationStartup(StartupProgressApplicationStartup.create(context.getApplicationStartup(), startupProgress, context.getBeanFactory()));
        ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
        // the lifecycle beans live as long as the context, so they hold what they act on rather than this listener
        StartupProgressServer pageServer = server;
        WebServerApplicationContext webContext = (WebServerApplicationContext) context;
        beanFactory.registerSingleton("grailsStartupProgressHandOff", StartupProgressLifecycle.beforeWebServer(() -> {
            startupProgress.startingWebServer();
            if (pageServer != null) {
                pageServer.stop();
            }
        }));
        beanFactory.registerSingleton("grailsStartupProgressInitializing", StartupProgressLifecycle.afterWebServer(() -> {
            startupProgress.initializing();
            if (webContext.getWebServer() != null) {
                // a random port is only known now, and names the cookie that signs a browser in to the report
                startupAccess.usePort(webContext.getWebServer().getPort());
            }
        }));
        beanFactory.registerSingleton("grailsStartupProgressFilter",
                StartupProgressFilter.registration(responder, servingPage));
        if (servingPage) {
            LOG.info("Startup progress is shown at {} until the application is ready", uri("http", port, "/", signInQuery()));
            openBrowser("http", port, signInQuery());
        }
    }

    /**
     * Serves the progress page until the web server takes the port over, and says whether it is served.
     */
    private boolean servePage(StartupProgressResponder responder, int port) {
        if (port <= 0) {
            LOG.debug("Not serving startup progress: server.port {} is not a fixed port", port);
            return false;
        }
        if (ssl) {
            LOG.debug("Not serving startup progress: the web server uses SSL");
            return false;
        }
        if (isCheckpointOnRefresh()) {
            // the checkpoint is taken before any lifecycle bean starts, so before the port would be handed over,
            // and a checkpoint fails on an open socket
            LOG.debug("Not serving startup progress: a checkpoint is taken on refresh");
            return false;
        }
        StartupProgressServer progressServer = new StartupProgressServer(address, port, responder);
        try {
            progressServer.start();
        }
        catch (IOException ex) {
            LOG.debug("Not serving startup progress: port {} could not be bound", port, ex);
            return false;
        }
        server = progressServer;
        return true;
    }

    @Override
    public void started(ConfigurableApplicationContext context, Duration timeTaken) {
        if (progress != null) {
            progress.started();
        }
        stopServer();
    }

    @Override
    public void ready(ConfigurableApplicationContext context, Duration timeTaken) {
        if (progress != null) {
            progress.ready();
        }
        stopServer();
        if (context instanceof WebServerApplicationContext webContext && webContext.getWebServer() != null) {
            String scheme = ssl ? "https" : "http";
            int port = webContext.getWebServer().getPort();
            if (reportPath != null) {
                // the details of the start are for whoever can read this log, so the address carries the token that shows them
                LOG.info("Startup report is at {}", uri(scheme, port, reportPath.substring(contextPath.length()), signInQuery()));
            }
            // a browser not opened on the progress page opens on the application itself
            openBrowser(scheme, port, null);
        }
    }

    @Override
    public void failed(ConfigurableApplicationContext context, Throwable exception) {
        if (progress == null) {
            return;
        }
        StartupProgress.Phase reached = progress.getPhase();
        progress.failed(exception);
        if (server == null) {
            // only the report was served, which nobody watches while the application starts
            return;
        }
        boolean answering = server.isRunning();
        if (!answering && (reached == StartupProgress.Phase.STARTING_WEB_SERVER || reached == StartupProgress.Phase.INITIALIZING)) {
            // a refresh that fails as the web server starts, or once it is up, stops the web server before run
            // listeners hear of it, so take the port back to say why; if the port is still held once the web server
            // is up, the failure came after the refresh and the web server's own filter is answering
            answering = restartServer() || reached == StartupProgress.Phase.INITIALIZING;
        }
        if (answering) {
            progress.awaitFailureDelivery(FAILURE_DELIVERY_TIMEOUT_MILLIS);
        }
        stopServer();
    }

    private boolean restartServer() {
        try {
            server.start();
            return true;
        }
        catch (IOException ex) {
            LOG.debug("Could not take the port back to report the failed start", ex);
            return false;
        }
    }

    private void openBrowser(String scheme, int port, String query) {
        List<String> command = browserCommand;
        if (command == null || port <= 0) {
            return;
        }
        browserCommand = null;
        URI uri = uri(scheme, port, "/", query);
        if (uri != null) {
            StartupBrowser.open(uri, command);
        }
    }

    /** The query that signs a browser in from the log, when there are details it would see by signing in. */
    private String signInQuery() {
        return access.isSignInRequired() ? access.signInQuery() : null;
    }

    private URI uri(String scheme, int port, String path, String query) {
        String host = address == null || address.isAnyLocalAddress() ? "localhost" : address.getHostAddress();
        try {
            return new URI(scheme, null, host, port, contextPath + path, query, null);
        }
        catch (URISyntaxException ex) {
            LOG.debug("No address to open a browser on for context path {}", contextPath, ex);
            return null;
        }
    }

    private void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    /** Whether Spring is to take a CRaC checkpoint when the context refreshes, as {@code -Dspring.context.checkpoint=onRefresh} asks. */
    private static boolean isCheckpointOnRefresh() {
        return DefaultLifecycleProcessor.ON_REFRESH_VALUE.equalsIgnoreCase(SpringProperties.getProperty(DefaultLifecycleProcessor.CHECKPOINT_PROPERTY_NAME));
    }

    private static boolean isSslEnabled(Binder binder) {
        boolean configured = binder.bind("server.ssl", Bindable.mapOf(String.class, Object.class)).map(ssl -> !ssl.isEmpty()).orElse(false);
        return configured && binder.bind("server.ssl.enabled", Boolean.class).orElse(true);
    }

    /**
     * The full path of one of the paths served, from the configured one below the context path. A path that
     * would take the place of the application's own root falls back to the default.
     */
    private String path(String configured, String defaultPath, String servedThere) {
        String path = contextPath(configured == null ? "" : configured);
        if (path.isEmpty()) {
            LOG.warn("Serving the {} at {} rather than at the application's root", servedThere, defaultPath);
            path = defaultPath;
        }
        return contextPath + path;
    }

    private static String contextPath(String configured) {
        String path = StringUtils.trimTrailingCharacter(configured.trim(), '/');
        return path.isEmpty() || path.startsWith("/") ? path : "/" + path;
    }

    private static String applicationName(Binder binder) {
        return binder.bind("spring.application.name", String.class)
                .orElseGet(() -> binder.bind("info.app.name", String.class).orElse("Grails application"));
    }
}
