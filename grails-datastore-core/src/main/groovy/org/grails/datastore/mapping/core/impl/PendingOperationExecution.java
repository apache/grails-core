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

package org.grails.datastore.mapping.core.impl;

import java.util.List;

/**
 * Provides a default implementation to execute a pending operation.
 *
 * @author Graeme Rocher
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public class PendingOperationExecution {

    public static void executePendingOperation(PendingOperation pendingOperation) {
        List<PendingOperation> preOperations = pendingOperation.getPreOperations();
        for (PendingOperation preOperation : preOperations) {
            preOperation.run();
        }
        pendingOperation.run();
        if(!pendingOperation.isVetoed()) {
            List<PendingOperation> cascadeOperations = pendingOperation.getCascadeOperations();
            for (PendingOperation cascadeOperation : cascadeOperations) {
                cascadeOperation.run();
            }
        }
    }
}
