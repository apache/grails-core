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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders the progress page from its template. The page is static apart from the application name
 * and a small JSON configuration block; everything that changes during startup is fetched by the page
 * and written into it as text, never as markup.
 */
final class StartupProgressPage {

    /** The page runs only its own inline script and style, and talks only to the application. */
    static final String CONTENT_SECURITY_POLICY =
            "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; img-src data:; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    /** How long a client turned away while the application starts is asked to wait before trying again. */
    static final String RETRY_AFTER_SECONDS = "5";

    private static final String TEMPLATE_RESOURCE = "startup-progress.html";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(APPLICATION_NAME|GRAILS_VERSION|CONFIG)}}");

    /** A part of the page included only for some requests, so it is not in the page at all for the others. */
    private static final Pattern SECTION = Pattern.compile("\\{\\{#(DETAILS|SIGN_IN)}}(.*?)\\{\\{/\\1}}", Pattern.DOTALL);

    private final String applicationName;

    private final String statusPath;

    private final String grailsVersion;

    /**
     * @param grailsVersion the version shown in the footer to a browser that may see the details of the start
     */
    StartupProgressPage(String applicationName, String statusPath, String grailsVersion) {
        this.applicationName = applicationName;
        this.statusPath = statusPath;
        this.grailsVersion = grailsVersion;
    }

    /**
     * The page for a request made with the given method. A page answering anything but a {@code GET}
     * or {@code HEAD} navigates to its own address when the application is ready rather than reloading,
     * so the browser does not offer to resubmit a form the application never received.
     *
     * @param showDetails whether the browser may see the details of the start, the Grails version among them
     * @param signInForDetails whether the browser would see the details by signing in
     */
    String render(String method, boolean showDetails, boolean signInForDetails) {
        boolean reload = "GET".equals(method) || "HEAD".equals(method);
        return render(showDetails, signInForDetails, ",\"reload\":" + reload);
    }

    /**
     * The report of a finished start, which shows the status it is given rather than polling for one.
     *
     * @param status the status of the start, as JSON
     */
    String renderReport(String status, boolean showDetails, boolean signInForDetails) {
        return render(showDetails, signInForDetails, ",\"report\":" + status);
    }

    private String render(boolean showDetails, boolean signInForDetails, String extraConfig) {
        String config = "{\"statusPath\":" + StartupProgress.string(statusPath) +
                ",\"phaseHeader\":" + StartupProgress.string(StartupProgress.PHASE_HEADER) + extraConfig + "}";
        Map<String, String> values = Map.of(
                "APPLICATION_NAME", escapeHtml(applicationName),
                "GRAILS_VERSION", escapeHtml(showDetails && grailsVersion != null ? grailsVersion : ""),
                "CONFIG", config);
        // the details are left out of the page, not hidden in it, for a browser that may not see them
        String sections = SECTION.matcher(Template.TEXT).replaceAll(match -> {
            boolean include = "DETAILS".equals(match.group(1)) ? showDetails : signInForDetails;
            return include ? Matcher.quoteReplacement(match.group(2)) : "";
        });
        return PLACEHOLDER.matcher(sections).replaceAll(match -> Matcher.quoteReplacement(values.get(match.group(1))));
    }

    /**
     * Whether a request for the startup report asks for it as data, with an {@code Accept} header naming
     * JSON but not HTML, or with a {@code format=json} parameter as Grails takes elsewhere.
     */
    static boolean wantsJson(String accept, String rawQuery) {
        if (rawQuery != null && Arrays.asList(rawQuery.split("&")).contains("format=json")) {
            return true;
        }
        return accept != null && accept.contains("application/json") && !accept.contains("text/html");
    }

    String plainText() {
        return applicationName + " is starting. Try again shortly.\n";
    }

    private static String escapeHtml(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '&' -> escaped.append("&amp;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }

    /** Loads the template the first time a page is rendered rather than on every start. */
    private static final class Template {

        static final String TEXT = load();

        private static String load() {
            try (InputStream in = StartupProgressPage.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
                if (in == null) {
                    throw new IllegalStateException("Missing startup progress page template " + TEMPLATE_RESOURCE);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }
    }
}
