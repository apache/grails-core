/*
 * Copyright 2004-2005 Graeme Rocher
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
package org.grails.taglib;

import org.grails.buffer.FastStringWriter;

/**
 * A temporary writer used by GSP to write to a StringWriter and later retrieve the value.
 * It also converts nulls into blank strings.
 *
 * @author Graeme Rocher
 * @since 0.5
 */
public class GroovyPageTagWriter extends FastStringWriter {

    private static final int DEFAULT_CHUNK_SIZE = Integer.getInteger("groovypagetagwriter.chunksize", 512);

    public GroovyPageTagWriter() {
        super(DEFAULT_CHUNK_SIZE);
    }

}
