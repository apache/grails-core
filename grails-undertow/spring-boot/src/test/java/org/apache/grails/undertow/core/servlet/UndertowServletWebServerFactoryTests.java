/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.grails.undertow.core.servlet;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;

import io.undertow.Undertow.Builder;
import io.undertow.server.HttpHandler;
import io.undertow.servlet.ServletExtension;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.core.ServletExtensionHolder;
import io.undertow.servlet.handlers.ServletRequestContext;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletRequestEvent;
import jakarta.servlet.ServletRequestListener;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.io.CloseMode;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.TrustStrategy;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import org.apache.grails.undertow.core.UndertowBuilderCustomizer;

import org.springframework.boot.web.error.ErrorPage;
import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.server.WebServerException;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.util.FileCopyUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link UndertowServletWebServerFactory}.
 */
class UndertowServletWebServerFactoryTests {

    private WebServer webServer;

    @AfterEach
    void stopServer() {
        if (this.webServer != null) {
            this.webServer.stop();
        }
    }

    @Test
    void startServerAndGetResponse() throws Exception {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        this.webServer = factory.getWebServer(helloWorldServletRegistration());
        this.webServer.start();
        assertThat(this.webServer.getPort()).isGreaterThan(0);
        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/hello")).isEqualTo("Hello World");
    }

    @Test
    void startServerWithContextPath() throws Exception {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory("/example", 0);
        this.webServer = factory.getWebServer(helloWorldServletRegistration());
        this.webServer.start();
        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/example/hello"))
            .isEqualTo("Hello World");
    }

    @Test
    void stopCalledTwiceIsSafe() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        this.webServer = factory.getWebServer(helloWorldServletRegistration());
        this.webServer.start();
        this.webServer.stop();
        this.webServer.stop();
    }

    @Test
    void builderCustomizersAreApplied() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        AtomicBoolean customized = new AtomicBoolean();
        factory.addBuilderCustomizers((UndertowBuilderCustomizer) (Builder builder) -> customized.set(true));
        this.webServer = factory.getWebServer(helloWorldServletRegistration());
        this.webServer.start();
        assertThat(customized).isTrue();
    }

    @Test
    void deploymentInfoCustomizersAreApplied() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        AtomicBoolean customized = new AtomicBoolean();
        factory.addDeploymentInfoCustomizers((deploymentInfo) -> customized.set(true));
        this.webServer = factory.getWebServer(helloWorldServletRegistration());
        this.webServer.start();
        assertThat(customized).isTrue();
    }

    @Test
    void accessLogIsWritten(@TempDir File accessLogDirectory) throws Exception {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.setAccessLogDirectory(accessLogDirectory);
        factory.setAccessLogEnabled(true);
        this.webServer = factory.getWebServer(helloWorldServletRegistration());
        this.webServer.start();
        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/hello")).isEqualTo("Hello World");
        File accessLog = new File(accessLogDirectory, "access_log.log");
        Awaitility.await()
            .untilAsserted(() -> assertThat(accessLogDirectory.listFiles()).contains(accessLog));
    }

    @Test
    void sslRequestIsServed() throws Exception {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        Ssl ssl = new Ssl();
        ssl.setKeyStore("classpath:org/apache/grails/undertow/core/servlet/test.jks");
        ssl.setKeyStorePassword("secret");
        ssl.setKeyPassword("password");
        factory.setSsl(ssl);
        this.webServer = factory.getWebServer(helloWorldServletRegistration());
        this.webServer.start();
        assertThat(getHttpsResponse("https://localhost:" + this.webServer.getPort() + "/hello"))
            .isEqualTo("Hello World");
    }

    @Test
    void temporaryDirectoriesAreDeletedWhenTheServerStops() throws Exception {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        UndertowServletWebServer webServer = (UndertowServletWebServer) factory
            .getWebServer(helloWorldServletRegistration());
        this.webServer = webServer;
        webServer.start();
        Deployment deployment = webServer.getDeploymentManager().getDeployment();
        Path tempDir = deployment.getDeploymentInfo().getTempDir().toPath();
        Path documentRoot = Path.of(deployment.getServletContext().getRealPath("/"));
        Files.writeString(tempDir.resolve("upload.tmp"), "upload");
        assertThat(tempDir).isDirectory();
        assertThat(documentRoot).isDirectory();
        webServer.stop();
        assertThat(tempDir).doesNotExist();
        assertThat(documentRoot).doesNotExist();
    }

    @Test
    void aServerThatNeverStartedIsUndeployedWhenItIsDestroyed() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(-1);
        AtomicBoolean contextDestroyed = new AtomicBoolean();
        ServletContextInitializer listenerRegistration = (servletContext) -> servletContext
            .addListener(new ServletContextListener() {

                @Override
                public void contextDestroyed(ServletContextEvent event) {
                    contextDestroyed.set(true);
                }

            });
        UndertowServletWebServer webServer = (UndertowServletWebServer) factory
            .getWebServer(helloWorldServletRegistration(), listenerRegistration);
        this.webServer = webServer;
        webServer.start();
        Deployment deployment = webServer.getDeploymentManager().getDeployment();
        Path tempDir = deployment.getDeploymentInfo().getTempDir().toPath();
        Path documentRoot = Path.of(deployment.getServletContext().getRealPath("/"));
        assertThat(tempDir).isDirectory();
        assertThat(documentRoot).isDirectory();
        webServer.destroy();
        assertThat(contextDestroyed).isTrue();
        assertThat(tempDir).doesNotExist();
        assertThat(documentRoot).doesNotExist();
    }

    @Test
    void theApplicationsListenersStillFindTheTemporaryDirectoryWhenTheContextIsDestroyed() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        AtomicBoolean tempDirExisted = new AtomicBoolean();
        ServletContextInitializer listenerRegistration = (servletContext) -> servletContext
            .addListener(new ServletContextListener() {

                @Override
                public void contextDestroyed(ServletContextEvent event) {
                    File tempDir = (File) event.getServletContext().getAttribute(ServletContext.TEMPDIR);
                    tempDirExisted.set(tempDir.isDirectory());
                }

            });
        UndertowServletWebServer webServer = (UndertowServletWebServer) factory
            .getWebServer(helloWorldServletRegistration(), listenerRegistration);
        this.webServer = webServer;
        webServer.start();
        Path tempDir = webServer.getDeploymentManager().getDeployment().getDeploymentInfo().getTempDir().toPath();
        webServer.stop();
        assertThat(tempDirExisted).as("the temporary directory while the application's listener is destroyed")
            .isTrue();
        assertThat(tempDir).doesNotExist();
    }

    @Test
    void aServerWhoseHandlersFailedToBuildIsUndeployedWhenItIsDestroyed(@TempDir File parent) throws Exception {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        File regularFile = new File(parent, "file");
        Files.writeString(regularFile.toPath(), "not a directory");
        factory.setAccessLogEnabled(true);
        factory.setAccessLogDirectory(new File(regularFile, "logs"));
        AtomicBoolean contextDestroyed = new AtomicBoolean();
        ServletContextInitializer listenerRegistration = (servletContext) -> servletContext
            .addListener(new ServletContextListener() {

                @Override
                public void contextDestroyed(ServletContextEvent event) {
                    contextDestroyed.set(true);
                }

            });
        UndertowServletWebServer webServer = (UndertowServletWebServer) factory
            .getWebServer(helloWorldServletRegistration(), listenerRegistration);
        this.webServer = webServer;
        Path tempDir = webServer.getDeploymentManager().getDeployment().getDeploymentInfo().getTempDir().toPath();
        assertThatExceptionOfType(WebServerException.class).isThrownBy(webServer::start);
        // what Spring Boot does with a server that failed to start
        webServer.stop();
        webServer.destroy();
        assertThat(contextDestroyed).isTrue();
        assertThat(tempDir).doesNotExist();
    }

    @Test
    void aConfiguredDocumentRootIsKeptWhenTheServerStops(@TempDir File documentRoot) throws Exception {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.setDocumentRoot(documentRoot);
        Files.writeString(documentRoot.toPath().resolve("index.html"), "Hello World");
        UndertowServletWebServer webServer = (UndertowServletWebServer) factory
            .getWebServer(helloWorldServletRegistration());
        this.webServer = webServer;
        webServer.start();
        Path tempDir = webServer.getDeploymentManager().getDeployment().getDeploymentInfo().getTempDir().toPath();
        assertThat(getResponse("http://localhost:" + webServer.getPort() + "/index.html")).isEqualTo("Hello World");
        webServer.stop();
        assertThat(tempDir).doesNotExist();
        assertThat(documentRoot.toPath().resolve("index.html")).hasContent("Hello World");
    }

    /**
     * Undertow, like Tomcat, leaves processing an error no error page maps to to the asynchronous timeout, and so does
     * the handler.
     */
    @Test
    void asynchronousProcessingWhoseRequestDispatchSentAnErrorNoErrorPageMapsIsLeftToTheAsynchronousTimeout()
            throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.addDeploymentInfoCustomizers((info) -> info.setDefaultAsyncContextTimeout(500));
        this.webServer = factory.getWebServer(sendsErrorAfterStartingAsync(events, false));
        this.webServer.start();

        assertThat(getStatus("http://localhost:" + this.webServer.getPort() + "/sendsError")).isEqualTo(503);

        Awaitility.await().untilAsserted(() -> assertThat(events).containsExactly("onTimeout", "onComplete"));
    }

    @Test
    void asynchronousProcessingCompletesOnceTheErrorPageHasAnsweredAnErrorItsRequestDispatchSent() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.addErrorPages(new ErrorPage("/hello"));
        this.webServer = factory.getWebServer(helloWorldServletRegistration(), sendsErrorAfterStartingAsync(events, false));
        this.webServer.start();

        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/sendsError")).isEqualTo("Hello World");

        Awaitility.await().untilAsserted(() -> assertThat(events).containsExactly("onComplete"));
    }

    @Test
    void asynchronousProcessingThatADispatchCompletesAfterSendingAnErrorCompletesOnce() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        this.webServer = factory.getWebServer(sendsErrorAfterStartingAsync(events, true));
        this.webServer.start();

        assertThat(getStatus("http://localhost:" + this.webServer.getPort() + "/sendsError")).isEqualTo(503);

        Awaitility.await().untilAsserted(() -> assertThat(events).containsExactly("onComplete"));
        Thread.sleep(200);
        assertThat(events).containsExactly("onComplete");
    }

    @Test
    void asynchronousProcessingThatADispatchCompletesAfterSendingAnErrorCompletesOnceTheErrorPageHasAnsweredIt()
            throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.addErrorPages(new ErrorPage("/errorPage"));
        this.webServer = factory.getWebServer(recordingErrorPage(events), sendsErrorAfterStartingAsync(events, true));
        this.webServer.start();

        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/sendsError")).isEqualTo("Error page");

        Awaitility.await().untilAsserted(() -> assertThat(events).containsExactly("errorPage", "onComplete"));
        Thread.sleep(200);
        assertThat(events).containsExactly("errorPage", "onComplete");
    }

    @Test
    void asynchronousProcessingWhoseAsynchronousDispatchSentAnErrorNoErrorPageMapsIsLeftToTheAsynchronousTimeout()
            throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.addDeploymentInfoCustomizers((info) -> info.setDefaultAsyncContextTimeout(500));
        this.webServer = factory.getWebServer(dispatchesAsynchronously(), sendsErrorAfterStartingAsync(events, false));
        this.webServer.start();

        assertThat(getStatus("http://localhost:" + this.webServer.getPort() + "/dispatches")).isEqualTo(503);

        Awaitility.await().untilAsserted(() -> assertThat(events).containsExactly("onTimeout", "onComplete"));
    }

    @Test
    void asynchronousProcessingCompletesOnceTheErrorPageHasAnsweredAnErrorItsAsynchronousDispatchSent()
            throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.addErrorPages(new ErrorPage("/errorPage"));
        this.webServer = factory.getWebServer(recordingErrorPage(events), dispatchesAsynchronously(),
            sendsErrorAfterStartingAsync(events, false));
        this.webServer.start();

        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/dispatches")).isEqualTo("Error page");

        Awaitility.await().untilAsserted(() -> assertThat(events).containsExactly("errorPage", "onComplete"));
    }

    @Test
    void processingAnAsynchronousDispatchCompletesAfterSendingAnErrorCompletesOnceTheErrorPageHasAnsweredIt()
            throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.addErrorPages(new ErrorPage("/errorPage"));
        this.webServer = factory.getWebServer(recordingErrorPage(events), dispatchesAsynchronously(),
            sendsErrorAfterStartingAsync(events, true));
        this.webServer.start();

        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/dispatches")).isEqualTo("Error page");

        Awaitility.await().untilAsserted(() -> assertThat(events).containsExactly("errorPage", "onComplete"));
        Thread.sleep(200);
        assertThat(events).containsExactly("errorPage", "onComplete");
    }

    @Test
    void asynchronousProcessingAnOuterHandlerCompletesAfterADispatchSentAnErrorCompletesOnceTheErrorPageHasAnsweredIt()
            throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.addErrorPages(new ErrorPage("/errorPage"));
        factory.addDeploymentInfoCustomizers((info) -> info.addOuterHandlerChainWrapper(
            (next) -> completesAfter(next, DispatcherType.REQUEST)));
        this.webServer = factory.getWebServer(recordingErrorPage(events), sendsErrorAfterStartingAsync(events, false));
        this.webServer.start();

        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/sendsError")).isEqualTo("Error page");

        Awaitility.await().untilAsserted(() -> assertThat(events).containsExactly("errorPage", "onComplete"));
        Thread.sleep(200);
        assertThat(events).containsExactly("errorPage", "onComplete");
    }

    @Test
    void processingCompletedOutsideAfterAnAsynchronousDispatchSentAnErrorCompletesOnceTheErrorPageHasAnsweredIt()
            throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.addErrorPages(new ErrorPage("/errorPage"));
        // Undertow runs outer handlers on a request dispatch only, and an inner one added later wraps the handler
        factory.addDeploymentInfoCustomizers((info) -> info.addInnerHandlerChainWrapper(
            (next) -> completesAfter(next, DispatcherType.ASYNC)));
        this.webServer = factory.getWebServer(recordingErrorPage(events), dispatchesAsynchronously(),
            sendsErrorAfterStartingAsync(events, false));
        this.webServer.start();

        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/dispatches")).isEqualTo("Error page");

        Awaitility.await().untilAsserted(() -> assertThat(events).containsExactly("errorPage", "onComplete"));
        Thread.sleep(200);
        assertThat(events).containsExactly("errorPage", "onComplete");
    }

    @ParameterizedTest
    @ValueSource(strings = { "/startsAsync", "/dispatches" })
    void processingCompletesOnceTheErrorPageHasAnsweredAnErrorAHandlerACustomizerAddedForwardedTo(String path)
            throws Exception {
        assertCompletesOnceTheErrorPageHasAnswered(path,
            UndertowServletWebServerFactoryTests::addHandlersThatForwardToAnErrorAndComplete);
    }

    @ParameterizedTest
    @ValueSource(strings = { "/startsAsync", "/dispatches" })
    void processingCompletesOnceTheErrorPageHasAnsweredAnErrorAHandlerAServletExtensionAddedForwardedTo(String path)
            throws Exception {
        assertCompletesOnceTheErrorPageHasAnswered(path, (info) -> info.addServletExtension(
            (deployment, servletContext) -> addHandlersThatForwardToAnErrorAndComplete(deployment)));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/startsAsync", "/dispatches" })
    void processingCompletesOnceTheErrorPageHasAnsweredAnErrorAHandlerAHeldServletExtensionAddedForwardedTo(String path)
            throws Exception {
        ServletExtension extension = (deployment, servletContext) ->
            addHandlersThatForwardToAnErrorAndComplete(deployment);
        ServletExtensionHolder.getServletExtensions().add(extension);
        try {
            assertCompletesOnceTheErrorPageHasAnswered(path, null);
        }
        finally {
            ServletExtensionHolder.getServletExtensions().remove(extension);
        }
    }

    @Test
    void asynchronousProcessingADispatchDispatchesAfterSendingAnErrorIsCompletedOnceTheDispatchItAskedForHasRun()
            throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        this.webServer = factory.getWebServer(dispatchesAfterSendingAnError(events));
        this.webServer.start();

        assertThat(getStatus("http://localhost:" + this.webServer.getPort() + "/dispatchesAfterSendingAnError"))
            .isEqualTo(503);

        Awaitility.await().untilAsserted(() -> assertThat(events)
            .containsExactly("dispatched", "sent again", "onComplete"));
        Thread.sleep(200);
        assertThat(events).containsExactly("dispatched", "sent again", "onComplete");
    }

    /**
     * Undertow's error dispatch completes the processing once the error page has run, which tells the listeners before
     * the dispatch the request asked for runs. That is Undertow's own, with or without the handler, which keeps the
     * error from being sent a second time once that dispatch has returned.
     */
    @Test
    void asynchronousProcessingADispatchDispatchesAfterSendingAnErrorAnErrorPageAnswersCompletesOnce()
            throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.addErrorPages(new ErrorPage("/errorPage"));
        this.webServer = factory.getWebServer(recordingErrorPage(events), dispatchesAfterSendingAnError(events));
        this.webServer.start();

        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/dispatchesAfterSendingAnError"))
            .isEqualTo("Error page");

        Awaitility.await().untilAsserted(() -> assertThat(events)
            .containsExactly("errorPage", "onComplete", "dispatched", "sent again"));
        Thread.sleep(200);
        assertThat(events).containsExactly("errorPage", "onComplete", "dispatched", "sent again");
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void aListenerThatFollowsAsynchronousProcessingStartedAgainHearsItEndOnce(boolean errorPage)
            throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        if (errorPage) {
            factory.addErrorPages(new ErrorPage("/errorPage"));
        }
        else {
            factory.addDeploymentInfoCustomizers((info) -> info.setDefaultAsyncContextTimeout(500));
        }
        ServletRegistrationBean<HttpServlet> first = new ServletRegistrationBean<>(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) {
                request.startAsync().addListener(new AsyncListener() {

                    @Override
                    public void onComplete(AsyncEvent event) {
                        events.add("first:onComplete");
                    }

                    @Override
                    public void onTimeout(AsyncEvent event) {
                        events.add("first:onTimeout");
                    }

                    @Override
                    public void onError(AsyncEvent event) {
                        events.add("first:onError");
                    }

                    @Override
                    public void onStartAsync(AsyncEvent event) {
                        events.add("first:onStartAsync");
                        event.getAsyncContext().addListener(this);
                    }

                });
                request.getAsyncContext().dispatch("/sendsError");
            }

        }, "/first");
        first.setName("first");
        first.setAsyncSupported(true);
        this.webServer = factory.getWebServer(recordingErrorPage(events), first,
            sendsErrorAfterStartingAsync(events, false));
        this.webServer.start();

        getResponse("http://localhost:" + this.webServer.getPort() + "/first");

        // Undertow's error dispatch completes the processing once the error page has run, which completes the
        // processing before it as well, before Undertow tells its listeners that the processing started again.
        // Without an error page, the processing is left to the asynchronous timeout
        List<String> expected = errorPage
            ? List.of("errorPage", "first:onComplete", "onComplete", "first:onStartAsync")
            : List.of("first:onStartAsync", "onTimeout", "first:onTimeout", "onComplete", "first:onComplete");
        Awaitility.await().untilAsserted(() -> assertThat(events).containsExactlyElementsOf(expected));
        Thread.sleep(200);
        assertThat(events).containsExactlyElementsOf(expected);
    }

    @Test
    void theErrorPageAnsweringAnErrorAnAsynchronousDispatchSentRunsWithinThatDispatch() throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.addErrorPages(new ErrorPage("/errorPage"));
        ServletRequestListener scopesAnAttribute = new ServletRequestListener() {

            @Override
            public void requestInitialized(ServletRequestEvent event) {
                event.getServletRequest().setAttribute("inDispatch", Boolean.TRUE);
            }

            @Override
            public void requestDestroyed(ServletRequestEvent event) {
                event.getServletRequest().removeAttribute("inDispatch");
            }

        };
        ServletRegistrationBean<HttpServlet> errorPage = new ServletRegistrationBean<>(new HttpServlet() {

            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
                events.add("errorPage in dispatch: " + (request.getAttribute("inDispatch") != null));
                response.getWriter().write("Error page");
            }

        }, "/errorPage");
        errorPage.setName("errorPage");
        this.webServer = factory.getWebServer(errorPage, dispatchesAsynchronously(),
            sendsErrorAfterStartingAsync(events, false), new ServletListenerRegistrationBean<>(scopesAnAttribute));
        this.webServer.start();

        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/dispatches")).isEqualTo("Error page");

        Awaitility.await().untilAsserted(() -> assertThat(events)
            .containsExactly("errorPage in dispatch: true", "onComplete"));
    }

    @Test
    void asynchronousProcessingADispatchLeftOpenAfterWritingAnErrorStatusItselfIsLeftToTheApplication()
            throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        ServletRegistrationBean<HttpServlet> writesNotFound = new ServletRegistrationBean<>(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                AsyncContext asyncContext = request.startAsync();
                asyncContext.addListener(recordingListener(events));
                response.setStatus(404);
                response.getWriter().write("Not here");
                response.getWriter().close();
                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                    }
                    catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                    events.add("completedByTheApplication");
                    asyncContext.complete();
                }).start();
            }

        }, "/writesNotFound");
        writesNotFound.setName("writesNotFound");
        writesNotFound.setAsyncSupported(true);
        this.webServer = factory.getWebServer(writesNotFound);
        this.webServer.start();

        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/writesNotFound"))
            .isEqualTo("Not here");

        Awaitility.await().untilAsserted(() -> assertThat(events)
            .containsExactly("completedByTheApplication", "onComplete"));
    }

    /**
     * Requests a path with handlers around it that forward to {@code /sendsErrorOnly} and then complete the processing
     * the dispatch to {@code /startsAsync} left started, and asserts that the listeners hear of it once, after the
     * error page has answered. {@code /startsAsync} is requested directly, or dispatched to from {@code /dispatches}.
     */
    private void assertCompletesOnceTheErrorPageHasAnswered(String path, UndertowDeploymentInfoCustomizer customizer,
            ServletContextInitializer... initializers) throws Exception {
        List<String> events = new CopyOnWriteArrayList<>();
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory(0);
        factory.addErrorPages(new ErrorPage("/errorPage"));
        if (customizer != null) {
            factory.addDeploymentInfoCustomizers(customizer);
        }
        List<ServletContextInitializer> all = new ArrayList<>(List.of(recordingErrorPage(events),
            dispatchesAsynchronously("/startsAsync"), startsAsync(events), sendsError()));
        all.addAll(Arrays.asList(initializers));
        this.webServer = factory.getWebServer(all.toArray(new ServletContextInitializer[0]));
        this.webServer.start();

        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + path)).isEqualTo("Error page");

        Awaitility.await().untilAsserted(() -> assertThat(events).containsExactly("errorPage", "onComplete"));
        Thread.sleep(200);
        assertThat(events).containsExactly("errorPage", "onComplete");
    }

    /**
     * Adds an outer handler, which runs on a request dispatch, and an inner one, which also runs on an asynchronous
     * dispatch, that forward to {@code /sendsErrorOnly} and then complete the processing.
     */
    private static void addHandlersThatForwardToAnErrorAndComplete(DeploymentInfo deployment) {
        deployment.addOuterHandlerChainWrapper(
            (next) -> forwardsAndCompletesAfter(next, DispatcherType.REQUEST, "/sendsErrorOnly"));
        deployment.addInnerHandlerChainWrapper(
            (next) -> forwardsAndCompletesAfter(next, DispatcherType.ASYNC, "/sendsErrorOnly"));
    }

    private ServletRegistrationBean<HttpServlet> dispatchesAfterSendingAnError(List<String> events) {
        ServletRegistrationBean<HttpServlet> registration = new ServletRegistrationBean<>(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                if (request.getDispatcherType() == DispatcherType.ASYNC) {
                    events.add("dispatched");
                    // Undertow ignores an error sent once an error has been sent and the response committed
                    response.sendError(503);
                    events.add("sent again");
                    return;
                }
                request.startAsync().addListener(recordingListener(events));
                response.sendError(503);
                request.getAsyncContext().dispatch();
            }

        }, "/dispatchesAfterSendingAnError");
        registration.setName("dispatchesAfterSendingAnError");
        registration.setAsyncSupported(true);
        return registration;
    }

    /**
     * Completes the asynchronous processing a dispatch of the given type left started, once the handlers it wraps
     * have returned.
     */
    private static HttpHandler completesAfter(HttpHandler next, DispatcherType completedAfter) {
        return forwardsAndCompletesAfter(next, completedAfter, null);
    }

    /**
     * Forwards a dispatch of the given type to {@code /startsAsync} that left asynchronous processing started to a
     * path, if there is one, once the handlers it wraps have returned, and then completes the processing. A dispatch to
     * {@code /dispatches} has dispatched its processing, which is not completed then.
     */
    private static HttpHandler forwardsAndCompletesAfter(HttpHandler next, DispatcherType completedAfter,
            String forwardTo) {
        return (exchange) -> {
            ServletRequestContext context = exchange.getAttachment(ServletRequestContext.ATTACHMENT_KEY);
            DispatcherType dispatcherType = context.getDispatcherType();
            ServletRequest request = context.getServletRequest();
            boolean dispatched = "/dispatches".equals(((HttpServletRequest) request).getServletPath());
            next.handleRequest(exchange);
            if (dispatcherType == completedAfter && request.isAsyncStarted() && !dispatched) {
                if (forwardTo != null) {
                    request.getRequestDispatcher(forwardTo).forward(request, context.getServletResponse());
                }
                request.getAsyncContext().complete();
            }
        };
    }

    private ServletRegistrationBean<HttpServlet> dispatchesAsynchronously() {
        return dispatchesAsynchronously("/sendsError");
    }

    private ServletRegistrationBean<HttpServlet> dispatchesAsynchronously(String path) {
        ServletRegistrationBean<HttpServlet> registration = new ServletRegistrationBean<>(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) {
                request.startAsync().dispatch(path);
            }

        }, "/dispatches");
        registration.setName("dispatches");
        registration.setAsyncSupported(true);
        return registration;
    }

    private ServletRegistrationBean<HttpServlet> recordingErrorPage(List<String> events) {
        ServletRegistrationBean<HttpServlet> registration = new ServletRegistrationBean<>(new HttpServlet() {

            @Override
            protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
                events.add("errorPage");
                response.getWriter().write("Error page");
            }

        }, "/errorPage");
        registration.setName("errorPage");
        return registration;
    }

    private static AsyncListener recordingListener(List<String> events) {
        return new AsyncListener() {

            @Override
            public void onComplete(AsyncEvent event) {
                events.add("onComplete");
            }

            @Override
            public void onTimeout(AsyncEvent event) {
                events.add("onTimeout");
            }

            @Override
            public void onError(AsyncEvent event) {
                events.add("onError");
            }

            @Override
            public void onStartAsync(AsyncEvent event) {
                events.add("onStartAsync");
            }

        };
    }

    private ServletRegistrationBean<HttpServlet> startsAsync(List<String> events) {
        ServletRegistrationBean<HttpServlet> registration = new ServletRegistrationBean<>(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) {
                request.startAsync().addListener(recordingListener(events));
            }

        }, "/startsAsync");
        registration.setName("startsAsync");
        registration.setAsyncSupported(true);
        return registration;
    }

    private ServletRegistrationBean<HttpServlet> sendsError() {
        ServletRegistrationBean<HttpServlet> registration = new ServletRegistrationBean<>(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                response.sendError(503);
            }

        }, "/sendsErrorOnly");
        registration.setName("sendsErrorOnly");
        registration.setAsyncSupported(true);
        return registration;
    }

    private ServletRegistrationBean<HttpServlet> sendsErrorAfterStartingAsync(List<String> events, boolean completes) {
        ServletRegistrationBean<HttpServlet> registration = new ServletRegistrationBean<>(new HttpServlet() {

            @Override
            protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
                request.startAsync().addListener(recordingListener(events));
                response.sendError(503);
                if (completes) {
                    request.getAsyncContext().complete();
                }
            }

        }, "/sendsError");
        registration.setName("sendsError");
        registration.setAsyncSupported(true);
        return registration;
    }

    private ServletRegistrationBean<HttpServlet> helloWorldServletRegistration() {
        ServletRegistrationBean<HttpServlet> registration = new ServletRegistrationBean<>(new HelloWorldServlet());
        registration.addUrlMappings("/hello");
        return registration;
    }

    private String getResponse(String url) throws IOException {
        try (CloseableHttpClient client = HttpClients.createMinimal()) {
            return readEntity(client, url);
        }
    }

    private int getStatus(String url) throws IOException {
        try (CloseableHttpClient client = HttpClients.createMinimal();
                CloseableHttpResponse response = client.execute(new HttpGet(url))) {
            return response.getCode();
        }
    }

    private String getHttpsResponse(String url) throws Exception {
        SSLContext sslContext = new SSLContextBuilder()
            .loadTrustMaterial(null, (TrustStrategy) (chain, authType) -> true)
            .build();
        SSLConnectionSocketFactory socketFactory = new SSLConnectionSocketFactory(sslContext,
            NoopHostnameVerifier.INSTANCE);
        CloseableHttpClient client = HttpClients.custom()
            .setConnectionManager(
                PoolingHttpClientConnectionManagerBuilder.create().setSSLSocketFactory(socketFactory).build())
            .build();
        try {
            return readEntity(client, url);
        } finally {
            client.close(CloseMode.GRACEFUL);
        }
    }

    private String readEntity(CloseableHttpClient client, String url) throws IOException {
        try (CloseableHttpResponse response = client.execute(new HttpGet(url))) {
            return new String(FileCopyUtils.copyToByteArray(response.getEntity().getContent()),
                StandardCharsets.UTF_8);
        }
    }

    static class HelloWorldServlet extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setContentType("text/plain");
            response.getWriter().print("Hello World");
        }

    }

}
