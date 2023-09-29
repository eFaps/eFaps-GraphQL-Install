/*
 * Copyright 2003 - 2023 The eFaps Team
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
 *
 */
package org.efaps.esjp.graphql;

import java.util.ArrayList;
import java.util.HashMap;
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
import org.efaps.graphql.definition.ObjectDef;
import org.efaps.graphql.providers.DataFetcherProvider;
import org.efaps.util.EFapsException;
import org.efaps.util.UUIDUtil;
import org.efaps.util.cache.CacheReloadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;

@EFapsUUID("c99ee1a6-2967-4cdb-a788-b6aa2630e52b")
@EFapsApplication("eFaps-GraphQL")
public class BaseCreateMutation
    implements DataFetcher<Object>
{

    private static final Logger LOG = LoggerFactory.getLogger(BaseCreateMutation.class);

    @Override
    public Object get(final DataFetchingEnvironment environment)
        throws Exception
    {
        LOG.info("Running mutation: {}", this);
        final var values = evalArgumentValues(environment);
        final var props = getProperties(environment);
        final var createType = evalCreateType(props);
        final var inst = executeStmt(createType, values);
        return inst == null ? null : inst.getOid();
    }

    protected Map<String, Object> evalArgumentValues(final DataFetchingEnvironment environment)
    {
        LOG.debug("Evaluating arguments");
        final var inputVariableName = environment.getFieldDefinition().getArguments().get(0).getName();
        final var inputObjectType = (GraphQLInputObjectType) environment.getFieldDefinition()
                        .getArguments().get(0).getType();
        final var inputObject = environment.<Map<String, Object>>getArgument(inputVariableName);
        return evalValues(environment, inputObjectType, inputObject);
    }

    @SuppressWarnings("unchecked")
    protected HashMap<String, Object> evalValues(final DataFetchingEnvironment environment,
                                                 final GraphQLInputObjectType inputObjectType,
                                                 final Map<String, Object> inputObject)
    {
        final var values = new HashMap<String, Object>();
        final var objectDefOpt = environment.getGraphQlContext().<ObjectDef>getOrEmpty(inputObjectType.getName());
        if (objectDefOpt.isPresent()) {
            final var objectDef = objectDefOpt.get();
            for (final var entry : objectDef.getFields().entrySet()) {
                final var fieldName = entry.getKey();
                if (inputObject.containsKey(fieldName)) {
                    final var inputFieldType = inputObjectType.getField(fieldName).getType();
                    // if it is a simple type
                    if (inputFieldType instanceof GraphQLScalarType || inputFieldType instanceof GraphQLNonNull) {
                        values.put(entry.getValue().getSelect(), inputObject.get(fieldName));
                    }
                    if (inputFieldType instanceof GraphQLList) {
                        final var wrappedType = ((GraphQLList) inputFieldType).getWrappedType();
                        if (wrappedType instanceof GraphQLInputObjectType) {
                            final var valueList = new ArrayList<Map<String, Object>>();
                            for (final var listEntry : (List<?>) inputObject.get(fieldName)) {
                                valueList.add(evalValues(environment, (GraphQLInputObjectType) wrappedType,
                                                (Map<String, Object>) listEntry));
                            }
                            values.put(entry.getValue().getSelect(), valueList);
                        } else {
                            LOG.error("What???");
                        }
                    }
                    if (inputFieldType instanceof GraphQLInputObjectType) {
                        final var fieldValue = evalValues(environment, (GraphQLInputObjectType) inputFieldType,
                                        (Map<String, Object>) inputObject.get(fieldName));
                        values.put(entry.getValue().getSelect(), fieldValue);
                    }
                }
            }
        }
        return values;
    }

    protected Properties getProperties(final DataFetchingEnvironment environment)
    {
        LOG.debug("Evaluating properties");
        final var parentTypeName = environment.getExecutionStepInfo().getObjectType().getName();
        final var fieldName = environment.getFieldDefinition().getName();
        final String contextKey = DataFetcherProvider.contextKey(parentTypeName, fieldName);
        final var props = environment.getGraphQlContext().getOrDefault(contextKey,
                        new HashMap<>());
        final var properties = new Properties();
        properties.putAll(props);
        LOG.debug("-> {}", props);
        return properties;
    }

    protected Type evalCreateType(Properties properties)
        throws CacheReloadException
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

        Insert stmt = null;
        if (select.startsWith("attributeset[")) {
            final var attrName = select.substring(13, select.length() - 1);
            final var attrSet = AttributeSet.find(parentInstance.getType().getName(), attrName);
            stmt = EQL.builder().insert(attrSet)
                            .set(attrName, Converter.convert(parentInstance));
        } else if (select.startsWith("class[")) {
            final var classTypeName = select.substring(6, select.length() - 1);
            final var classification = Classification.get(classTypeName);

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
        }
        return stmt.execute();
    }
}
