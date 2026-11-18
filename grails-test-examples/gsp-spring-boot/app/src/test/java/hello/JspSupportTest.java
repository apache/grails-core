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

import jakarta.servlet.ServletContext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * What decides whether the JSP half of the example is offered. An executable jar packages no JSP
 * and cannot compile one, yet it does have a (temporary, empty) document root, so the answer comes
 * from whether the page is in the servlet context rather than from whether a real path exists.
 */
class JspSupportTest {

    @Test
    void aContextHoldingTheJspCanServeIt() throws Exception {
        ServletContext servletContext = mock(ServletContext.class);
        given(servletContext.getResource(JspSupport.JSP_VIEW)).willReturn(URI.create("file:/app/form.jsp").toURL());

        assertThat(JspSupport.canServeJsp(servletContext)).isTrue();
    }

    @Test
    void aContextWithoutTheJspCannotServeIt() throws Exception {
        ServletContext servletContext = mock(ServletContext.class);
        given(servletContext.getResource(JspSupport.JSP_VIEW)).willReturn(null);

        assertThat(JspSupport.canServeJsp(servletContext)).isFalse();
    }

    @Test
    void noServletContextCannotServeIt() {
        assertThat(JspSupport.canServeJsp(null)).isFalse();
    }
}
