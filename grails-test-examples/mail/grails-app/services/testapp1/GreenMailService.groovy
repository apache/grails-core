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

package testapp1

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetupTest
import groovy.util.logging.Slf4j
import jakarta.annotation.PreDestroy

@Slf4j
class GreenMailService {

    GreenMail greenMail

    void start() {
        greenMail = new GreenMail(ServerSetupTest.SMTP)
        greenMail.start()
        log.info('Greenmail started on port {}', greenMail.getSmtp().getPort())
    }

    void reset() {
        if (greenMail) {
            greenMail.reset()
            log.debug('Greenmail reset')
        }
    }

    @PreDestroy
    void stop() {
        if (greenMail) {
            greenMail.stop()
            log.info('Greenmail stopped')
        }
    }
}