/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package grails.util;

import java.lang.management.ManagementFactory;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Asserts that {@code -XX:ActiveProcessorCount} from the root build's
 * {@code jvmArgumentProviders} actually reaches this forked test JVM.
 * Wholesale {@code jvmArgs = ...} assignments in module scripts would drop a
 * flag added via {@code jvmArgs}, which is why the build uses a provider.
 */
class ActiveProcessorCountForkTests {

    private static final String FLAG_PREFIX = "-XX:ActiveProcessorCount=";

    @Test
    void forkedTestJvmReceivesActiveProcessorCount() {
        String flag = findActiveProcessorCountFlag();
        assertNotNull(flag,
                "forked test JVM must receive -XX:ActiveProcessorCount from jvmArgumentProviders");
        int advertised = Integer.parseInt(flag.substring(FLAG_PREFIX.length()));
        assertTrue(advertised >= 2, "floor is 2 so HotSpot keeps G1: " + advertised);
        assertEquals(advertised, Runtime.getRuntime().availableProcessors(),
                "HotSpot must honour the advertised processor count");
    }

    private static String findActiveProcessorCountFlag() {
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String arg : args) {
            if (arg.startsWith(FLAG_PREFIX)) {
                return arg;
            }
        }
        return null;
    }
}
