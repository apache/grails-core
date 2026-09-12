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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JSP half of the example, as a run from the project directory serves it: {@code src/main/webapp}
 * is in the servlet context here, the same as it is in a war. An executable jar packages no JSP, and
 * neither offers the link nor renders one - covered by {@link JspSupportTest}, since a test cannot
 * take the page out of a servlet context it did not pack.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JspViewTest {

    @Value("${local.server.port}")
    int port;

    @AfterEach
    void backToGsp() throws Exception {
        // the selected rendering is application-wide state, so leave it as the other tests expect it
        get("/gsp");
    }

    @Test
    void theFormOffersItsJspRendering() throws Exception {
        assertThat(get("/")).contains("href=\"/jsp\"");
    }

    @Test
    void theJspRendersDecoratedByTheGspLayout() throws Exception {
        get("/jsp");

        String body = get("/");
        // the heading is the layout's, carrying the view type of what it decorated
        assertThat(body).containsPattern("<h1[^>]*>Rendered by JSP</h1>");
        assertThat(body).contains("<title>Decorated");
        assertThat(body).contains("href=\"/gsp\"");
    }

    private String get(String path) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }
}
