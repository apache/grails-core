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
package org.springframework.security.access.vote

import groovy.transform.CompileStatic

import org.springframework.context.support.MessageSourceAccessor
import org.springframework.security.access.AccessDecisionManager
import org.springframework.security.access.AccessDecisionVoter
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.ConfigAttribute
import org.springframework.security.core.SpringSecurityMessageSource

@CompileStatic
abstract class AbstractAccessDecisionManager implements AccessDecisionManager {

    protected final MessageSourceAccessor messages = SpringSecurityMessageSource.accessor
    protected final List<AccessDecisionVoter> decisionVoters

    boolean allowIfAllAbstainDecisions

    AbstractAccessDecisionManager(List<AccessDecisionVoter> decisionVoters) {
        this.decisionVoters = decisionVoters == null ? [] : new ArrayList<>(decisionVoters)
    }

    List<AccessDecisionVoter> getDecisionVoters() {
        decisionVoters
    }

    @Override
    boolean supports(ConfigAttribute attribute) {
        decisionVoters.any { it.supports(attribute) }
    }

    @Override
    boolean supports(Class<?> clazz) {
        decisionVoters.every { it.supports(clazz) }
    }

    protected void checkAllowIfAllAbstainDecisions() {
        if (!allowIfAllAbstainDecisions) {
            throw new AccessDeniedException(
                    messages.getMessage(
                            'AbstractAccessDecisionManager.accessDenied',
                            'Access is denied'
                    )
            )
        }
    }
}
