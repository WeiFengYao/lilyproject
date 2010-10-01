/*
 * Copyright 2010 Outerthought bvba
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lilycms.indexer.model.indexerconf;

import org.lilycms.repository.api.*;

public class FieldValue extends BaseValue {
    private FieldType fieldType;

    protected FieldValue(FieldType fieldType, boolean extractContent, String formatter) {
        super(extractContent, formatter);
        this.fieldType = fieldType;
    }

    public ValueType getValueType() {
        return fieldType.getValueType();
    }

    public FieldType getFieldType() {
        return fieldType;
    }

    public String getFieldDependency() {
        return fieldType.getId();
    }

    public FieldType getTargetFieldType() {
        return fieldType;
    }
}