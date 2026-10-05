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
package hello;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The error page a browser gets. Spring Boot renders it through the view named {@code error}, and
 * the GSP view resolver is asked for that view first: with no {@code error.gsp} it must let Boot's
 * page answer rather than forward to an {@code /error} JSP that does not exist - which is the URL
 * already being handled, and a loop the container refuses.
 *
 * <p>A client that sends no {@code Accept} header is given Boot's JSON error body instead, which
 * never asks a view resolver, so the header is what reaches the path under test.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/gsp")
class ErrorPageTest {

    @Value("${local.server.port}")
    int port;

    @ParameterizedTest
    @ValueSource(strings = {"/does-not-exist", "/nested/does-not-exist"})
    void anUnmappedPathRendersSpringBootsErrorPage(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/gsp" + path))
                .header("Accept", "text/html")
                .GET()
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains("Whitelabel Error Page");
    }
}
