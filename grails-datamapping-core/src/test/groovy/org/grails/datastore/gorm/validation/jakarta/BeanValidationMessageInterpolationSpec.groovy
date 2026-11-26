/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.grails.datastore.gorm.validation.jakarta

import jakarta.validation.Validation
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import org.hibernate.validator.HibernateValidator
import spock.lang.Specification
import spock.lang.Unroll

class BeanValidationMessageInterpolationSpec extends Specification {

    @Unroll
    void 'interpolates the standard decimal-bound message for #propertyName'() {
        given:
        def factory = Validation.byProvider(HibernateValidator).configure()
                .defaultLocale(Locale.ENGLISH).buildValidatorFactory()
        def amount = new Amount((propertyName): value)

        when:
        def violations = factory.validator.validate(amount)

        then:
        violations.size() == 1
        violations.first().propertyPath.toString() == propertyName
        violations.first().message == message

        cleanup:
        factory?.close()

        where:
        propertyName       | value || message
        'minimum'          | 1.0G  || 'must be greater than or equal to 1.5'
        'exclusiveMinimum' | 1.5G  || 'must be greater than 1.5'
        'maximum'          | 5.0G  || 'must be less than or equal to 4.5'
        'exclusiveMaximum' | 4.5G  || 'must be less than 4.5'
    }

    static class Amount {

        @DecimalMin('1.5')
        BigDecimal minimum

        @DecimalMin(value = '1.5', inclusive = false)
        BigDecimal exclusiveMinimum

        @DecimalMax('4.5')
        BigDecimal maximum

        @DecimalMax(value = '4.5', inclusive = false)
        BigDecimal exclusiveMaximum
    }
}
