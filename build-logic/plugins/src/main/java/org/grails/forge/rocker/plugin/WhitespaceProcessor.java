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

package org.grails.forge.rocker.plugin;

import com.fizzed.rocker.model.PlainText;
import com.fizzed.rocker.model.PostProcessorException;
import com.fizzed.rocker.model.TemplateModel;
import com.fizzed.rocker.model.TemplateModelPostProcessor;
import com.fizzed.rocker.model.TemplateUnit;

import java.util.List;

public class WhitespaceProcessor implements TemplateModelPostProcessor {

    @Override
    public TemplateModel process(TemplateModel templateModel, int ppIndex) throws PostProcessorException {
        List<TemplateUnit> units = templateModel.getUnits();
        int length = units.size();
        PlainText lastPlainText = null;
        for (int i = 0; i < length; i ++) {
            TemplateUnit tu = units.get(i);
            if (tu instanceof PlainText) {
                PlainText pt = (PlainText)tu;
                if ((lastPlainText == null || lastPlainText.getText().endsWith("\n")) && pt.getText().startsWith("\n")) {
                    PlainText replacementPt = new PlainText(pt.getSourceRef(), pt.getText().substring(1));
                    // replace the unit
                    units.add(i, replacementPt);
                    units.remove(i + 1);
                }
                lastPlainText = pt;
            }
        }
        return templateModel;
    }
}
