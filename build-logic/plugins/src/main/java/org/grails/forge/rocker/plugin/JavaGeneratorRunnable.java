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

package org.grails.forge.rocker.plugin;

import com.fizzed.rocker.compiler.GeneratorException;
import com.fizzed.rocker.compiler.JavaGenerator;
import com.fizzed.rocker.compiler.JavaGeneratorMain;
import com.fizzed.rocker.compiler.PlainTextStrategy;
import com.fizzed.rocker.compiler.RockerConfiguration;
import com.fizzed.rocker.compiler.RockerUtil;
import com.fizzed.rocker.compiler.TemplateParser;
import com.fizzed.rocker.model.TemplateModel;
import com.fizzed.rocker.runtime.ParserException;
import com.fizzed.rocker.runtime.RockerRuntime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class JavaGeneratorRunnable implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(JavaGeneratorMain.class);

    private final com.fizzed.rocker.compiler.RockerConfiguration configuration;
    private final TemplateParser parser;
    private final JavaGenerator generator;
    private final List<File> templateFiles;
    private String suffixRegex;
    private boolean failOnError;

    public JavaGeneratorRunnable() {
        this.configuration = new RockerConfiguration();
        this.parser = new TemplateParser(this.configuration);
        this.generator = new JavaGenerator(this.configuration);
        this.generator.setPlainTextStrategy(PlainTextStrategy.STATIC_STRINGS);
        this.templateFiles = new ArrayList<>();
        this.suffixRegex = ".*\\.rocker\\.(raw|html)$";
        this.failOnError = true;
    }

    public String getSuffixRegex() {
        return suffixRegex;
    }

    public void setSuffixRegex(String suffixRegex) {
        this.suffixRegex = suffixRegex;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    public TemplateParser getParser() {
        return parser;
    }

    public JavaGenerator getGenerator() {
        return generator;
    }

    public void run() throws RuntimeException{
        if (this.configuration.getTemplateDirectory() == null) {
            throw new RuntimeException("Template directory was null");
        }

        if (!this.configuration.getTemplateDirectory().exists() || !this.configuration.getTemplateDirectory().isDirectory()) {
            throw new RuntimeException("Template directory does not exist: " + this.configuration.getTemplateDirectory());
        }

        // loop thru template directory and match templates
        Collection<File> allFiles = RockerUtil.listFileTree(this.configuration.getTemplateDirectory());
        for (File f : allFiles) {
            if (f.getName().matches(suffixRegex)) {
                templateFiles.add(f);
            }
        }

        log.info("Parsing " + templateFiles.size() + " rocker template files");

        int errors = 0;
        int generated = 0;

        for (File f : templateFiles) {
            TemplateModel model = null;

            try {
                // parse model
                model = parser.parse(f);
            } catch (IOException | ParserException e) {
                if (e instanceof ParserException) {
                    ParserException pe = (ParserException) e;
                    log.error("Parsing failed for " + f + ":[" + pe.getLineNumber() + "," + pe.getColumnNumber() + "] " + pe.getMessage());
                } else {
                    log.error("Unable to parse template", e);
                }
                errors++;
            }

            try {
                File outputFile = generator.generate(model);
                generated++;

                log.debug("Generated java source: " + outputFile);
            } catch (GeneratorException | IOException e) {
                throw new RuntimeException("Generating java source failed for " + f + ": " + e.getMessage(), e);
            }

        }

        log.info("Generated " + generated + " rocker java source files");

        if (errors > 0 && failOnError) {
            throw new RuntimeException("Caught " + errors + " errors.");
        }

        if (!configuration.getOptions().getOptimize()) {
            // save configuration
            this.configuration.getClassDirectory().mkdirs();

            // use resource name, but strip leading slash
            // place it into the classes directory (not the compile directory)
            try{
                File configFile = new File(this.configuration.getClassDirectory(), RockerRuntime.CONF_RESOURCE_NAME.substring(1));
                this.configuration.write(configFile);
                log.info("Generated rocker configuration " + configFile);
            }catch(IOException iox){
                throw new RuntimeException(iox);
            }
        } else {
            log.info("Optimize flag on. Did not generate rocker configuration file");
        }
    }

    public void setTemplateDirectory(File templateDirectory) {
        this.parser.getConfiguration().setTemplateDirectory(templateDirectory);
    }

    public void setOutputDirectory(File outputDirectory) {
        this.generator.getConfiguration().setOutputDirectory(outputDirectory);
    }
}
