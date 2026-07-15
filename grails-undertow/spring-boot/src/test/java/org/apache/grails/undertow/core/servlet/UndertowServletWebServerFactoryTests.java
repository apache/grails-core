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
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.net.ssl.SSLContext;

import io.undertow.Undertow.Builder;

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

import org.apache.grails.undertow.core.UndertowBuilderCustomizer;

import org.springframework.boot.web.server.Ssl;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.util.FileCopyUtils;

import static org.assertj.core.api.Assertions.assertThat;

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
