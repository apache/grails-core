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
package org.grails.forge.template;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class Config4kTemplate implements Template {
    private final String path;
    private final Config config;

    public Config4kTemplate(String path, Map<String, Object> values) {
        this.path = path;
        config = ConfigFactory.parseMap(values);
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public void write(OutputStream outputStream) throws IOException {
        String renderedConfig = config.root().render(renderOptions());
        outputStream.write(renderedConfig.getBytes(StandardCharsets.UTF_8));
    }

    private ConfigRenderOptions renderOptions() {
        ConfigRenderOptions options = ConfigRenderOptions.defaults();
        options = options.setOriginComments(false);
        return options.setJson(false);
    }
}
