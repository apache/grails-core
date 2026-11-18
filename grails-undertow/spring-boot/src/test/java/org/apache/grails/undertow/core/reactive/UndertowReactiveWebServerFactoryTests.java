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

package org.apache.grails.undertow.core.reactive;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import io.undertow.Undertow.Builder;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import org.apache.grails.undertow.core.UndertowBuilderCustomizer;

import org.springframework.boot.web.server.WebServer;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.util.FileCopyUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link UndertowReactiveWebServerFactory}.
 */
class UndertowReactiveWebServerFactoryTests {

    private WebServer webServer;

    @AfterEach
    void stopServer() {
        if (this.webServer != null) {
            this.webServer.stop();
        }
    }

    @Test
    void startServerAndGetResponse() throws Exception {
        UndertowReactiveWebServerFactory factory = new UndertowReactiveWebServerFactory(0);
        this.webServer = factory.getWebServer(helloWorldHandler());
        this.webServer.start();
        assertThat(this.webServer.getPort()).isGreaterThan(0);
        assertThat(getResponse("http://localhost:" + this.webServer.getPort() + "/")).isEqualTo("Hello World");
    }

    @Test
    void builderCustomizersAreApplied() {
        UndertowReactiveWebServerFactory factory = new UndertowReactiveWebServerFactory(0);
        AtomicBoolean customized = new AtomicBoolean();
        factory.addBuilderCustomizers((UndertowBuilderCustomizer) (Builder builder) -> customized.set(true));
        this.webServer = factory.getWebServer(helloWorldHandler());
        this.webServer.start();
        assertThat(customized).isTrue();
    }

    @Test
    void stopCalledTwiceIsSafe() {
        UndertowReactiveWebServerFactory factory = new UndertowReactiveWebServerFactory(0);
        this.webServer = factory.getWebServer(helloWorldHandler());
        this.webServer.start();
        this.webServer.stop();
        this.webServer.stop();
    }

    private HttpHandler helloWorldHandler() {
        return (request, response) -> {
            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            DataBuffer buffer = response.bufferFactory().wrap("Hello World".getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        };
    }

    private String getResponse(String url) throws IOException {
        try (CloseableHttpClient client = HttpClients.createMinimal();
             CloseableHttpResponse response = client.execute(new HttpGet(url))) {
            return new String(FileCopyUtils.copyToByteArray(response.getEntity().getContent()),
                StandardCharsets.UTF_8);
        }
    }

}
