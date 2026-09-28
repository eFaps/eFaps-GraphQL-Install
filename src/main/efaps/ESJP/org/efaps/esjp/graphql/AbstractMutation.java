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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.eql.EQL;
import org.efaps.eql.builder.Converter;
import org.efaps.eql2.bldr.AbstractUpdateEQLBuilder;
import org.efaps.graphql.definition.FieldDef;
import org.efaps.graphql.definition.ObjectDef;
import org.efaps.graphql.util.Utils;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedInputType;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLScalarType;

@EFapsUUID("2ab24ee2-1a3f-4a83-bddc-77a10a6ec495")
@EFapsApplication("eFaps-GraphQL")
public abstract class AbstractMutation
    extends AbstractDataFetcher
{

    private static final Logger LOG = LoggerFactory.getLogger(AbstractMutation.class);

    protected Map<String, Object> evalArgumentValues(final DataFetchingEnvironment environment,
                                                     final Properties props)
    {
        LOG.debug("Evaluating arguments: {}", environment.getArguments());
        final var inputVariableName = props.getProperty("InputVariable", "input");
        Map<String, Object> values;
        if (environment.containsArgument(inputVariableName) && environment.getArguments().size() == 1) {
            final var inputObjectType = (GraphQLInputObjectType) environment.getFieldDefinition()
                            .getArgument(inputVariableName).getType();
            final var inputObject = environment.<Map<String, Object>>getArgument(inputVariableName);
            values = evalValues(environment, inputObjectType, inputObject, null);
        } else if (environment.getArguments().size() > 0) {
            values = new HashMap<>();
            for (final var argumentValue : environment.getArguments().entrySet()) {
                final var argument = environment.getFieldDefinition().getArgument(argumentValue.getKey());
                final var inputType = argument.getType();
                values.putAll(evalValues(environment, inputType, argumentValue.getValue(), argument.getName()));
            }
        } else {
            values = new HashMap<>();
        }
        LOG.info("argument values: {}", values);
        return values;
    }

    @SuppressWarnings("unchecked")
    protected HashMap<String, Object> evalValues(final DataFetchingEnvironment environment,
                                                 final GraphQLInputType inputType,
                                                 final Object inputValue,
                                                 final String argumentName)
    {
        LOG.debug("Evaluating argument: {} with: {} - {}", inputType, inputValue, argumentName);
        final var values = new HashMap<String, Object>();
        if (inputType instanceof final GraphQLNamedInputType namedInputType) {
            if (namedInputType instanceof final GraphQLInputObjectType inputObjectType) {
                final var inputObject = (Map<String, Object>) inputValue;
                final var objectDefOpt = environment.getGraphQlContext()
                                .<ObjectDef>getOrEmpty(namedInputType.getName());
                if (objectDefOpt.isPresent()) {
                    final var objectDef = objectDefOpt.get();
                    for (final var entry : objectDef.getFields().entrySet()) {
                        final var fieldName = entry.getKey();
                        if (inputObject.containsKey(fieldName)) {
                            final var inputFieldType = inputObjectType.getField(fieldName).getType();
                            // if it is a simple type
                            if (inputFieldType instanceof GraphQLScalarType
                                            || inputFieldType instanceof GraphQLNonNull) {
                                values.put(getKey(entry.getValue()), inputObject.get(fieldName));
                            }
                            if (inputFieldType instanceof GraphQLList) {
                                final var wrappedType = ((GraphQLList) inputFieldType).getWrappedType();
                                if (wrappedType instanceof GraphQLInputObjectType) {
                                    final var valueList = new ArrayList<Map<String, Object>>();
                                    for (final var listEntry : (List<?>) inputObject.get(fieldName)) {
                                        valueList.add(evalValues(environment, (GraphQLInputObjectType) wrappedType,
                                                        (Map<String, Object>) listEntry, null));
                                    }
                                    values.put(getKey(entry.getValue()), valueList);
                                } else if (wrappedType instanceof GraphQLScalarType
                                                || wrappedType instanceof GraphQLNonNull) {
                                    final var valueList = new ArrayList<>();
                                    for (final var listEntry : (List<?>) inputObject.get(fieldName)) {
                                        valueList.add(listEntry);
                                    }
                                    values.put(getKey(entry.getValue()), valueList);
                                } else {
                                    LOG.error("What???");
                                }
                            }
                            if (inputFieldType instanceof GraphQLInputObjectType) {
                                final var fieldValue = evalValues(environment, inputFieldType,
                                                inputObject.get(fieldName), null);
                                values.put(getKey(entry.getValue()), fieldValue);
                            }
                        }
                    }
                }
            } else {
                LOG.warn("This is not expected", namedInputType);
            }
        } else {
            // get the root mutation definition
            final var fieldDefinition = environment.getFieldDefinition();
            final var objectDefOpt = environment.getGraphQlContext().<ObjectDef>getOrEmpty(Utils.MUTATIONNAME);
            if (objectDefOpt.isPresent()) {
                final var mutationObject = objectDefOpt.get();
                final var fieldDef = mutationObject.getFields().get(fieldDefinition.getName());
                final var argOpt = fieldDef.getArguments().stream().filter(arg -> arg.getName().equals(argumentName))
                                .findFirst();
                if (argOpt.isPresent()) {
                    final var argObject = argOpt.get();
                    final var key = StringUtils.isEmpty(argObject.getKey()) ? argObject.getName() : argObject.getKey();
                    values.put(key, inputValue);
                }
            }
        }
        LOG.debug("Values: {}", values);
        return values;
    }

    protected String getKey(final FieldDef fieldDef)
    {
        return StringUtils.isEmpty(fieldDef.getSelect()) ? fieldDef.getName() : fieldDef.getSelect();
    }

    protected void evalLinkto(final Type type,
                              final AbstractUpdateEQLBuilder<?> eqlBldr,
                              final Object value,
                              final String linkto)
        throws EFapsException
    {
        LOG.debug("Evaluating linkto: {}", linkto);
        final var linktoPattern = Pattern.compile("linkto\\[([\\w\\d]+).*");
        final var attrPattern = Pattern.compile("attribute\\[([\\w\\d]+).*");
        final var linkMatcher = linktoPattern.matcher(linkto);
        final var attrMatcher = attrPattern.matcher(linkto);
        linkMatcher.find();
        attrMatcher.find();
        final var linkAttrName = linkMatcher.group(1);
        final var linkAttr = type.getAttribute(linkAttrName);
        final var linktoType = linkAttr.getLink();
        final var attrName = attrMatcher.group(1);

        final String crit = String.valueOf(value);

        final var eval = EQL.builder().print()
                        .query(linktoType.getName())
                        .where()
                        .attribute(attrName).eq(crit)
                        .select().instance()
                        .evaluate();
        if (eval.next()) {
            eqlBldr.set(linkAttrName, Converter.convert(eval.inst()));
        }
    }

}
