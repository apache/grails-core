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

package functionaltests.commanddi

import grails.gorm.transactions.Transactional

/**
 * Service for custom validation logic that can be injected into command objects.
 */
@Transactional(readOnly = true)
class ValidationHelperService {

    /**
     * Check if a username is available (not already taken).
     */
    boolean isUsernameAvailable(String username) {
        // Simulate checking database - reserved usernames
        def reservedNames = ['admin', 'root', 'system', 'administrator']
        return username && !reservedNames.contains(username.toLowerCase())
    }

    /**
     * Validate email domain against allowed domains.
     */
    boolean isEmailDomainAllowed(String email) {
        if (!email || !email.contains('@')) return false
        def domain = email.split('@')[1]?.toLowerCase()
        def allowedDomains = ['example.com', 'company.com', 'test.org']
        return allowedDomains.contains(domain)
    }

    /**
     * Check if age is within acceptable range for registration.
     */
    boolean isAgeValid(Integer age) {
        return age != null && age >= 18 && age <= 120
    }

    /**
     * Normalize and validate a phone number.
     */
    Map<String, Object> validatePhoneNumber(String phone) {
        if (!phone) {
            return [valid: false, normalized: null, error: 'Phone number is required']
        }
        // Remove non-digits
        def digits = phone.replaceAll('[^0-9]', '')
        if (digits.length() < 10) {
            return [valid: false, normalized: null, error: 'Phone number too short']
        }
        if (digits.length() > 15) {
            return [valid: false, normalized: null, error: 'Phone number too long']
        }
        return [valid: true, normalized: digits, error: null]
    }

    /**
     * Get service identifier for testing.
     */
    String getServiceId() {
        return 'ValidationHelperService-v1'
    }
}
