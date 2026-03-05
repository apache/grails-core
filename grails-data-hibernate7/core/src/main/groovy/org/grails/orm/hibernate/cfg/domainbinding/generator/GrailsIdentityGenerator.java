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
package org.grails.orm.hibernate.cfg.domainbinding.generator;

import java.io.Serial;
import java.util.Optional;
import java.util.Properties;

import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.IdentityGenerator;

import org.grails.orm.hibernate.cfg.Identity;

public class GrailsIdentityGenerator extends IdentityGenerator {

  @Serial
  private static final long serialVersionUID = 1L;

    public GrailsIdentityGenerator(GeneratorCreationContext context, Identity mappedId) {
        var generatorProps =
                Optional.ofNullable(mappedId).map(Identity::getProperties).orElse(new Properties());
        super.configure(context, generatorProps);
        context.getProperty().getValue().getColumns().get(0).setIdentity(true);
    }
}
