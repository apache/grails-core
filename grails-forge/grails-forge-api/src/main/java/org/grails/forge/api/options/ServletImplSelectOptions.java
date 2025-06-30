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
package org.grails.forge.api.options;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grails.forge.api.SelectOptionDTO;
import org.grails.forge.api.ServletImplDTO;

import java.util.List;

@Schema(name = "ServletImplSelectOptions")
public class ServletImplSelectOptions extends SelectOptionDTO<ServletImplDTO> {
    public ServletImplSelectOptions(List<ServletImplDTO> options, ServletImplDTO defaultOption) {
        super(options, defaultOption);
    }

    @Override
    public List<ServletImplDTO> getOptions() {
        return super.getOptions();
    }

    @Override
    public ServletImplDTO getDefaultOption() {
        return super.getDefaultOption();
    }
}
