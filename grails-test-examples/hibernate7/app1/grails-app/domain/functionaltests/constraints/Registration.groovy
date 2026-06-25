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

package functionaltests.constraints

/**
 * Domain class demonstrating custom validator closures:
 * - Simple value validators
 * - Cross-field validators
 * - Collection validators
 * - Conditional validators
 * - Custom error messages
 */
class Registration {

    String username
    String password
    String confirmPassword
    String email
    Date birthDate
    Integer age
    String phone
    String country
    String state
    List<String> interests
    boolean termsAccepted
    String promoCode

    Date dateCreated
    Date lastUpdated

    static hasMany = [interests: String]

    static constraints = {
        // Simple custom validator - username must start with letter
        username blank: false, size: 4..20, validator: { val ->
            if (!val) return true
            if (!Character.isLetter(val.charAt(0))) {
                return 'registration.username.mustStartWithLetter'
            }
            // Username cannot contain special characters except underscore
            if (val =~ /[^a-zA-Z0-9_]/) {
                return 'registration.username.invalidCharacters'
            }
        }

        // Password strength validator
        password blank: false, minSize: 8, validator: { val ->
            if (!val) return true
            def hasUpper = val =~ /[A-Z]/
            def hasLower = val =~ /[a-z]/
            def hasDigit = val =~ /[0-9]/
            if (!hasUpper || !hasLower || !hasDigit) {
                return 'registration.password.weak'
            }
        }

        // Cross-field validator - confirmPassword must match password
        confirmPassword validator: { val, obj ->
            if (val != obj.password) {
                return 'registration.confirmPassword.mismatch'
            }
        }

        email email: true, blank: false

        // Age validator using birth date - cross-field validation
        birthDate validator: { val, obj ->
            if (!val) return true
            def today = new Date()
            def age = today.year - val.year
            if (age < 13) {
                return 'registration.birthDate.tooYoung'
            }
            if (age > 120) {
                return 'registration.birthDate.invalid'
            }
        }

        // Calculated field validation
        age nullable: true, validator: { val, obj ->
            if (val != null && obj.birthDate) {
                def today = new Date()
                def calculatedAge = today.year - obj.birthDate.year
                if (val != calculatedAge) {
                    return 'registration.age.doesNotMatchBirthDate'
                }
            }
        }

        // Phone number with conditional format based on country
        phone nullable: true, validator: { val, obj ->
            if (!val) return true
            if (obj.country == 'US') {
                // US phone format: (XXX) XXX-XXXX or XXX-XXX-XXXX
                if (!(val =~ /^(\(\d{3}\)\s?|\d{3}[-.])\d{3}[-.]?\d{4}$/)) {
                    return 'registration.phone.invalidUSFormat'
                }
            } else if (obj.country == 'UK') {
                // UK phone format
                if (!(val =~ /^(\+44|0)\d{10,11}$/)) {
                    return 'registration.phone.invalidUKFormat'
                }
            }
        }

        country inList: ['US', 'UK', 'CA', 'AU', 'DE', 'FR', 'Other']

        // Conditional validation - state required only for US
        state nullable: true, validator: { val, obj ->
            if (obj.country == 'US' && !val) {
                return 'registration.state.required'
            }
        }

        // Collection validator - interests must have at least one item
        interests nullable: true, validator: { val ->
            if (val != null && val.isEmpty()) {
                return 'registration.interests.empty'
            }
            if (val != null && val.size() > 5) {
                return 'registration.interests.tooMany'
            }
        }

        // Boolean validator
        termsAccepted validator: { val ->
            if (!val) {
                return 'registration.termsAccepted.required'
            }
        }

        // Optional promo code with format validation
        promoCode nullable: true, validator: { val ->
            if (!val) return true
            // Promo code format: PROMO-XXXX (4 alphanumeric characters)
            if (!(val =~ /^PROMO-[A-Z0-9]{4}$/)) {
                return 'registration.promoCode.invalidFormat'
            }
        }
    }
}
