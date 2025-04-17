/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.taglib.encoder;

import org.grails.core.io.support.GrailsFactoriesLoader;

/**
 * Used to load the {@link OutputContextLookup} class for GSP
 *
 * @author Lari Hotari
 * @author Graeme Rocher
 */
public class OutputContextLookupHelper {
    private static final OutputContextLookup outputContextLookup;

    static {
        OutputContextLookup foundViaFactory = GrailsFactoriesLoader.loadFactory(OutputContextLookup.class, OutputContextLookupHelper.class.getClassLoader());
        if(foundViaFactory != null) {
            outputContextLookup = foundViaFactory;
        }
        else {
            outputContextLookup = new DefaultOutputContextLookup();
        }
    }


    private OutputContextLookupHelper() {
    }

    public static OutputContext lookupOutputContext() {
        return outputContextLookup.lookupOutputContext();
    }
    public static OutputContextLookup getOutputContextLookup() {
        return outputContextLookup;
    }
}
