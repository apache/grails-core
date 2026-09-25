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
package org.apache.grails.scaffolding;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeSet;

import groovy.text.GStringTemplateEngine;

/**
 * Expands a scaffolding template into a page, and names the page the build compiles from it so
 * that the runtime resolver can find it.
 *
 * <p>The build and the resolver both come here, so a page is expanded and named by the same code
 * whichever of them does it. A page is named for the template's path, for readability, and for a
 * digest of the template and of the value of each name of the model the template mentions. A
 * template the build did not see, or saw expanded with a different model, so names a page that
 * does not exist rather than a different one. A name the template never mentions is left out, so
 * that a value that differs between the machine that built the application and the one running it
 * - {@code packagePath} follows the file separator - does not cost the page; a template that reads
 * the model without naming what it reads, through {@code binding} for instance, is named as though
 * it did not read it.</p>
 *
 * @since 8.0
 */
public final class ScaffoldedPages {

    /**
     * The directory under the views root that holds the compiled pages. A controller's views are
     * resolved from a directory named after a Java identifier, which cannot contain a hyphen, so no
     * request for a controller's view reaches a page here.
     */
    public static final String DIRECTORY = "grails-scaffolded";

    /**
     * Bytes of the digest kept in a page's name. The name already carries the domain class and the
     * template path, so the digest only has to tell apart the copies of one template, and the
     * models one template is expanded with; eight bytes do that with room to spare and keep the
     * compiled page's class names - which carry the domain class and the digest both - short of the
     * path lengths some tools stumble on.
     */
    private static final int KEY_BYTES = 8;

    private ScaffoldedPages() {
    }

    /**
     * The URI, relative to the views root, of the page expanded from a template with a model.
     *
     * @param templatePath the template's path under {@code templates/scaffolding}, without its
     *     extension, such as {@code show} or {@code admin/show}
     * @param model the names the template is expanded with, including {@code fullName}
     * @param template the template exactly as it is read
     * @return the URI a page locator resolves the page by
     */
    public static String uri(String templatePath, Map<String, ?> model, byte[] template) {
        return "/" + DIRECTORY + "/" + model.get("fullName") + "/" + templatePath + "-" + key(model, template) + ".gsp";
    }

    /**
     * Expands a template with a model, as both the build and the resolver do. A template is read
     * as UTF-8 wherever it is expanded, so the page the build compiles is the page the resolver
     * would have expanded, whatever encoding either JVM defaults to.
     *
     * @param template the template exactly as it is read
     * @param model the names the template is expanded with
     * @return the page
     */
    public static String expand(byte[] template, Map<String, ?> model) {
        StringWriter page = new StringWriter();
        try {
            new GStringTemplateEngine()
                    .createTemplate(new String(template, StandardCharsets.UTF_8))
                    .make(model)
                    .writeTo(page);
        }
        catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Could not expand the scaffolding template for " + model.get("fullName"), e);
        }
        return page.toString();
    }

    private static String key(Map<String, ?> model, byte[] template) {
        String text = new String(template, StandardCharsets.UTF_8);
        MessageDigest digest = sha256();
        digest.update(template);
        digest.update((byte) 0);
        for (String name : new TreeSet<>(model.keySet())) {
            if (!text.contains(name)) {
                continue;
            }
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(String.valueOf(model.get(name)).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest(), 0, KEY_BYTES);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required of every Java platform", e);
        }
    }
}
