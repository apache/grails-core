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
package grails.plugin.springsecurity.rest.token.storage.memcached

import groovy.transform.CompileStatic
import net.spy.memcached.transcoders.SerializingTranscoder

@CompileStatic
public class CustomSerializingTranscoder extends SerializingTranscoder{

    @Override
    protected Object deserialize(byte[] bytes) {
        final ClassLoader currentClassLoader = Thread.currentThread().contextClassLoader
        ObjectInputStream inputStream

        try {
            ByteArrayInputStream bs = new ByteArrayInputStream(bytes)
            inputStream = new ObjectInputStream(bs) {
                @Override
                protected Class<?> resolveClass(ObjectStreamClass objectStreamClass) throws IOException, ClassNotFoundException {
                    try {
                        return currentClassLoader.loadClass(objectStreamClass.name)
                    } catch (Exception ignored) {
                        return super.resolveClass(objectStreamClass)
                    }
                }
            };
            return inputStream.readObject()
        } catch (Exception e) {
            e.printStackTrace()
            throw new RuntimeException(e)
        } finally {
            closeStream(inputStream)
        }
    }

    private static void closeStream(Closeable c) {
        if (c != null) {
            try {
                c.close()
            } catch (IOException e) {
                e.printStackTrace()
            }
        }
    }
}
