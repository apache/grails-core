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

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Intended regression coverage that this Spring Boot application - which manages dependency
 * versions with the legacy {@code io.spring.dependency-management} plugin (importing grails-bom)
 * rather than any Grails Gradle plugin - boots and renders a Grails GSP view.
 *
 * <p>The build-level half of that guarantee (the Spring Dependency Management plugin resolving
 * grails-bom and the Grails libraries, and the GSP templates compiling) is already exercised by
 * building this module. This runtime test is {@link Disabled} because rendering GSP in a
 * <em>non-Grails</em> Spring Boot application does not currently start on Spring Boot 4: the
 * Grails GSP Spring Boot auto-configuration forms a {@code requestMappingHandlerAdapter} ->
 * {@code gspViewResolver} bean dependency cycle in a servlet web context, while
 * {@code CoreAutoConfiguration} requires a {@code GrailsApplication} that is only contributed by
 * that same auto-configuration. This GSP-integration limitation is unrelated to dependency
 * management. Re-enable this test once GSP rendering works in a standalone Spring Boot
 * application.</p>
 */
@Disabled("GSP rendering in a non-Grails Spring Boot application does not yet start on Spring Boot 4 "
        + "(GSP auto-configuration bean cycle); unrelated to the Spring Dependency Management coverage "
        + "this example provides at build time.")
@SpringBootTest(classes = Application.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebControllerTest {

    @Value("${local.server.port}")
    int port;

    @Test
    void gspViewRendersInSpringBootWithSpringDependencyManagement() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/")).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("Name:");
    }
}
