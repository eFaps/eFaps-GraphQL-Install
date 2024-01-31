/*
 * Copyright © 2003 - 2024 The eFaps Team (-)
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
package org.efaps.esjp.graphql;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.efaps.admin.datamodel.AttributeSet;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.eql.EQL;
import org.efaps.eql.builder.Converter;
import org.efaps.eql.builder.Insert;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.util.EFapsException;
import org.efaps.util.UUIDUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.execution.DataFetcherResult;
import graphql.execution.DataFetcherResult.Builder;
import graphql.schema.DataFetchingEnvironment;

@EFapsUUID("c99ee1a6-2967-4cdb-a788-b6aa2630e52b")
@EFapsApplication("eFaps-GraphQL")
public class BaseCreateMutation
    extends AbstractMutation
{

    private static final Logger LOG = LoggerFactory.getLogger(BaseCreateMutation.class);

    @Override
    public Object get(final DataFetchingEnvironment environment)
        throws Exception
    {
        LOG.info("Running mutation: {}", this);
        final var resultBldr = DataFetcherResult.newResult();
        final var props = getProperties(environment);
        final var values = evalArgumentValues(environment, props);
        if (validateValues(environment, values, resultBldr)) {
            final var createType = evalCreateType(props);
            final var inst = executeStmt(createType, values);
            if (InstanceUtils.isValid(inst)) {
                resultBldr.data(inst.getOid());
            }
        }
        return resultBldr.build();
    }

    protected boolean validateValues(final DataFetchingEnvironment environment,
                                     final Map<String, Object> values,
                                     final Builder<Object> resultBldr)
        throws EFapsException
    {
        return true;
    }

    protected Type evalCreateType(final Properties properties)
        throws EFapsException
    {
        Type ret;
        final var typeStr = properties.getProperty("Type");
        if (UUIDUtil.isUUID(typeStr)) {
            ret = Type.get(UUID.fromString(typeStr));
        } else {
            ret = Type.get(typeStr);
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    protected Instance executeStmt(final Type type,
                                   final Map<String, Object> values)
        throws EFapsException
    {
        LOG.debug("Execute base stmt");
        final var stmt = EQL.builder().insert(type);
        for (final var entry : values.entrySet()) {
            final var field = entry.getKey().trim();
            if (field.startsWith("attribute[")) {
                final String attrName = field.substring(10, field.length() - 1);
                stmt.set(attrName, Converter.convert(entry.getValue()));
            }
            if (field.startsWith("linkto[")) {
                evalLinkto(type, stmt, entry.getValue(), field);
            }
        }
        final var inst = stmt.execute();
        for (final var entry : values.entrySet()) {
            final var field = entry.getKey().trim();
            if (field.startsWith("attributeset[")) {
                for (final var valuesEntry : (List<Map<String, Object>>) entry.getValue()) {
                    executeStmt(inst, field, valuesEntry);
                }
            }
            if (field.startsWith("class[")) {
                executeStmt(inst, field, (Map<String, Object>) entry.getValue());
            }
        }
        return inst;
    }

    protected Instance executeStmt(final Instance parentInstance,
                                   final String select,
                                   final Map<String, Object> values)
        throws EFapsException
    {

        Type type = null;
        Insert stmt = null;
        if (select.startsWith("attributeset[")) {
            final var attrName = select.substring(13, select.length() - 1);
            final var attrSet = AttributeSet.find(parentInstance.getType().getName(), attrName);
            stmt = EQL.builder().insert(attrSet)
                            .set(attrName, Converter.convert(parentInstance));
            type = attrSet;
        } else if (select.startsWith("class[")) {
            final var classTypeName = select.substring(6, select.length() - 1);
            final var classification = Classification.get(classTypeName);
            type = classification;
            EQL.builder().insert(classification.getClassifyRelationType())
                            .set(classification.getRelLinkAttributeName(), Converter.convert(parentInstance))
                            .set(classification.getRelTypeAttributeName(), Converter.convert(classification.getId()))
                            .execute();
            stmt = EQL.builder().insert(classification)
                            .set(classification.getLinkAttributeName(), Converter.convert(parentInstance));
        }

        for (final var entry : values.entrySet()) {
            final var field = entry.getKey().trim();
            if (field.startsWith("attribute[")) {
                final String attrName = field.substring(10, field.length() - 1);
                stmt.set(attrName, Converter.convert(entry.getValue()));
            }
            if (field.startsWith("linkto[")) {
                evalLinkto(type, stmt, entry.getValue(), field);
            }
        }
        return stmt.execute();
    }

}
