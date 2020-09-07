/*
 * Copyright 2003 - 2020 The eFaps Team
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
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.eql.EQL;
import org.efaps.eql.builder.Where;
import org.efaps.eql2.EQL2;
import org.efaps.eql2.IWhereElementTerm;
import org.efaps.eql2.impl.PrintQueryStatement;
import org.efaps.esjp.common.properties.PropertiesUtil;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.graphql.definition.ArgumentDef;
import org.efaps.graphql.definition.FieldDef;
import org.efaps.graphql.definition.ObjectDef;
import org.efaps.graphql.providers.DataFetcherProvider;
import org.jfree.util.Log;

import graphql.GraphQLContext;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLType;

@EFapsUUID("3c405c05-9940-4eea-8d59-959b4890f4b3")
@EFapsApplication("eFaps-GraphQL")
public abstract class BaseDataFetcher_Base
    implements DataFetcher<Object>
{

    @Override
    public Object get(final DataFetchingEnvironment _environment)
        throws Exception
    {
        final var resultBldr = DataFetcherResult.newResult();
        final List<Map<String, Object>> values = new ArrayList<>();
        final var fieldName = _environment.getFieldDefinition().getName();
        final var parentTypeName = _environment.getExecutionStepInfo().getFieldContainer().getName();
        final Optional<ObjectDef> baseObjectDefOpt = ((GraphQLContext) _environment.getContext())
                        .getOrEmpty(parentTypeName);
        final var argumentDefs = new ArrayList<ArgumentDef>();
        if (baseObjectDefOpt.isPresent()) {
            final FieldDef fieldDef = baseObjectDefOpt.get().getFields().get(fieldName);
            if (fieldDef != null) {
                argumentDefs.addAll(fieldDef.getArguments());
            }
        }
        final String contextKey = DataFetcherProvider.contextKey(parentTypeName, fieldName);
        final var props = ((GraphQLContext) _environment.getContext()).getOrDefault(contextKey,
                        new HashMap<String, String>());
        final var properties = new Properties();
        properties.putAll(props);
        final Map<Integer, String> types = PropertiesUtil.analyseProperty(properties, "Type", 0);
        final Map<Integer, String> linkFroms = PropertiesUtil.analyseProperty(properties, "LinkFrom", 0);

        final var localContext = getLocalContext(_environment);

        GraphQLType graphQLType = _environment.getExecutionStepInfo().getFieldDefinition().getType();
        if (graphQLType instanceof GraphQLList) {
            graphQLType = ((GraphQLList) graphQLType).getWrappedType();
        }
        final var graphTypeName = ((GraphQLNamedType) graphQLType).getName();
        final Optional<ObjectDef> objectDefOpt = ((GraphQLContext) _environment.getContext()).getOrEmpty(graphTypeName);
        if (objectDefOpt.isPresent()) {
            final var objectDef = objectDefOpt.get();
            final var query = EQL.builder().print().query(types.values().toArray(new String[types.values().size()]));
            Where where = null;
            if (!linkFroms.isEmpty()) {
                final Instance parentInstance = (Instance) ((Map<?, ?>) _environment.getSource())
                                .get("currentInstance");
                if (InstanceUtils.isValid(parentInstance)) {
                    where = query.where();
                    where.attr(linkFroms.values().iterator().next()).eq(parentInstance);

                }
            }
            for (final var entry : _environment.getArguments().entrySet()) {
                final var argDefOpt = argumentDefs.stream().filter(en -> en.getName().equals(entry.getKey()))
                                .findFirst();
                if (argDefOpt.isPresent()) {
                    if (where == null) {
                        where = query.where();
                    } else {
                        where.and();
                    }

                    final var stmt = (PrintQueryStatement) EQL2.parse("print query type TYPE where "
                                    + String.format(argDefOpt.get().getWhereStmt(), entry.getValue())
                                    + " select attribute[OID]");
                    final var tmp = where.attr(((IWhereElementTerm) stmt.getQuery().getWhere().getTerms(0))
                                    .element().getAttribute());

                    switch (((IWhereElementTerm) stmt.getQuery().getWhere().getTerms(0)).element().getComparison()) {
                        case EQUAL:
                            tmp.eq((String) entry.getValue());
                            break;
                        case GREATER:
                            tmp.greater((String) entry.getValue());
                            break;
                        case GREATEREQ:
                            tmp.greaterOrEq((String) entry.getValue());
                            break;
                        case LESS:
                            tmp.less((String) entry.getValue());
                            break;
                        case LESSEQ:
                            tmp.lessOrEq((String) entry.getValue());
                            break;
                        case LIKE:
                            tmp.like((String) entry.getValue());
                            break;
                        case IN:
                            tmp.in((String) entry.getValue());
                            break;
                        case NOTIN:
                            tmp.notin((String) entry.getValue());
                            break;
                        case UNEQUAL:
                            tmp.uneq((String) entry.getValue());
                            break;
                        default:
                            Log.error("Not working");
                    }
                }
            }
            final var print = query.select();
            for (final var selectedField : _environment.getSelectionSet().getFields()) {
                if (objectDef.getFields().containsKey(selectedField.getName())) {
                    final FieldDef fieldDef = objectDef.getFields().get(selectedField.getName());
                    if (StringUtils.isNotBlank(fieldDef.getSelect())) {
                        print.select(fieldDef.getSelect()).as(selectedField.getName());
                    }
                }
            }
            final var eval = print.evaluate();
            while (eval.next()) {
                final var map = new HashMap<String, Object>();
                for (final var selectedField : _environment.getSelectionSet().getFields()) {
                    map.put(selectedField.getName(), eval.get(selectedField.getName()));
                }
                map.put("currentInstance", eval.inst());
                values.add(map);
            }
        }
        return resultBldr.data(values)
                        .localContext(localContext)
                        .build();
    }

    protected Map<String, Object> getLocalContext(final DataFetchingEnvironment _environment)
    {
        Map<String, Object> ret;
        if (_environment.getLocalContext() == null) {
            ret = new HashMap<>();
        } else {
            ret = _environment.getLocalContext();
        }
        return ret;
    }
}
