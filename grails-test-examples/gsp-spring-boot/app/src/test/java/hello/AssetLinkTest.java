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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The urls the asset pipeline's tags write in an application deployed under a context path. The tags
 * build them with a Grails link generator, which in a Grails application reads the context path off
 * the Grails request; this application renders GSP without being a Grails application, so the links
 * have to carry the context path all the same, or every stylesheet and script is requested from where
 * the application is not.
 */
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "server.servlet.context-path=/gsp")
class AssetLinkTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void assetLinksCarryTheContextPathAndAreServed() throws Exception {
        String page = get("/gsp/");

        String stylesheet = linkedUrl(page, "href", "application", ".css");
        String script = linkedUrl(page, "src", "theme", ".js");

        assertThat(stylesheet).startsWith("/gsp/assets/");
        assertThat(script).startsWith("/gsp/assets/");
        assertThat(get(stylesheet)).contains("--bs-");
        assertThat(get(script)).contains("data-bs-theme");
    }

    /** The url an attribute of the page links an asset by, whatever comes before its name. */
    private static String linkedUrl(String page, String attribute, String name, String extension) {
        Matcher matcher = Pattern.compile(attribute + "=\"([^\"]*/assets/" + name + "[^\"]*" + Pattern.quote(extension) + "[^\"]*)\"")
                .matcher(page);
        assertThat(matcher.find()).as("a link to %s%s in the page", name, extension).isTrue();
        return matcher.group(1);
    }

    private String get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as("GET %s", path).isEqualTo(200);
        return response.body();
    }
}
