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

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression coverage that this Spring Boot application - which manages dependency versions with
 * the legacy {@code io.spring.dependency-management} plugin (importing grails-bom) rather than any
 * Grails Gradle plugin - boots and renders a Grails GSP view, layout decoration included.
 *
 * <p>The build-level half of that guarantee (the Spring Dependency Management plugin resolving
 * grails-bom and the Grails libraries, and the GSP templates compiling) is exercised by building
 * this module; this test covers the runtime half in a <em>non-Grails</em> Spring Boot application.</p>
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebControllerTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void gspViewRendersInSpringBootWithSpringDependencyManagement() throws Exception {
        String body = get("/");

        assertThat(body).contains("<label for=\"name\"");
        assertThat(body).contains("id=\"name\"");
    }

    @Test
    void aTagLibraryWrittenForGrailsIsRegisteredAndRuns() throws Exception {
        // <asset:stylesheet> belongs to the asset pipeline, a tag library this application declares
        // as beans rather than installs as a plugin. An unregistered namespace is not a failure in
        // GSP - the tag is written out as it stands - so what proves it registered is that no tag of
        // that namespace reaches the browser.
        String body = get("/");

        assertThat(body).doesNotContain("<asset:");
    }

    @Test
    void theLayoutOffersTheThemeTheViewerPrefers() throws Exception {
        String body = get("/");

        // the menu is the layout's; theme.js applies the choice to Bootstrap's data-bs-theme
        assertThat(body).contains("data-bs-theme-value=\"light\"");
        assertThat(body).contains("data-bs-theme-value=\"dark\"");
        assertThat(body).contains("data-bs-theme-value=\"auto\"");
    }

    @Test
    void gspViewIsDecoratedByItsSiteMeshLayout() throws Exception {
        String body = get("/");

        // layouts/main.gsp wraps the view; layouts/sample.gsp is applied inline by <g:applyLayout>
        assertThat(body).contains("<title>Decorated");
        assertThat(body).contains("Sample Inline Layout");
        // the grailsLayout namespace the GSP compiler emits must be a registered tag library,
        // otherwise the capture tags reach the browser as literal markup
        assertThat(body).doesNotContain("grailsLayout:");
    }

    @Test
    void theFormIsSubmittedAndItsResultRendered() throws Exception {
        // as a browser does: the person is put in the session, which the results view reads back
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager())
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();
        HttpRequest submit = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("name=Ada&age=36"))
                .build();

        HttpResponse<String> response = client.send(submit, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Congratulations Ada!");
        // the results view is rendered by a view controller rather than by a handler method, and is
        // told what renders it all the same
        assertThat(response.body()).containsPattern("<h1[^>]*>Rendered by GSP</h1>");
    }

    @Test
    void whatTheFormPostsToCarriesNoSessionId() throws Exception {
        // a visitor arrives without a session cookie, and the container would otherwise rewrite the
        // action to carry the session id - a path Spring MVC has no mapping for
        String body = get("/");

        assertThat(body).doesNotContain("jsessionid");
    }

    private String get(String path) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        return response.body();
    }
}
