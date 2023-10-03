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
import java.util.regex.Pattern;

import org.efaps.admin.datamodel.Type;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.eql.EQL;
import org.efaps.eql.builder.Converter;
import org.efaps.eql2.bldr.AbstractUpdateEQLBuilder;
import org.efaps.graphql.definition.ObjectDef;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLList;
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
        LOG.debug("Evaluating arguments");
        final var inputVariableName = props.getProperty("InputVariable", "input");
        final var inputObjectType = (GraphQLInputObjectType) environment.getFieldDefinition()
                        .getArgument(inputVariableName).getType();
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
