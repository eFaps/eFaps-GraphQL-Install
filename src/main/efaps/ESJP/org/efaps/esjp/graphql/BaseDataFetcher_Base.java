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

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.eql.EQL;
import org.efaps.graphql.definition.FieldDef;
import org.efaps.graphql.definition.ObjectDef;
import org.efaps.graphql.providers.DataFetcherProvider;

import graphql.GraphQLContext;
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
        final List<Map<String, Object>> ret = new ArrayList<>();
        final var fieldName = _environment.getFieldDefinition().getName();
        final var parentTypeName = _environment.getExecutionStepInfo().getFieldContainer().getName();
        final String contextKey = DataFetcherProvider.contextKey(parentTypeName, fieldName);
        final var properties = ((GraphQLContext) _environment.getContext()).getOrDefault(contextKey,
                        new HashMap<String, String>());
        final var type = properties.get("Type01");

        GraphQLType graphQLType = _environment.getExecutionStepInfo().getFieldDefinition().getType();
        if (graphQLType instanceof GraphQLList) {
            graphQLType = ((GraphQLList) graphQLType).getWrappedType();
        }
        final var graphTypeName = ((GraphQLNamedType) graphQLType).getName();
        final Optional<ObjectDef> objectDefOpt = ((GraphQLContext) _environment.getContext()).getOrEmpty(graphTypeName);
        if (objectDefOpt.isPresent()) {
            final var objectDef = objectDefOpt.get();
            final var print = EQL.builder().print().query(type).select();
            for (final var selectedField : _environment.getSelectionSet().getFields()) {
                if (objectDef.getFields().containsKey(selectedField.getName())) {
                    final FieldDef fieldDef = objectDef.getFields().get(selectedField.getName());
                    print.select(fieldDef.getSelect()).as(selectedField.getName());
                }
            }
            final var eval = print.evaluate();
            while (eval.next()) {
                final var map = new HashMap<String, Object>();
                for (final var selectedField : _environment.getSelectionSet().getFields()) {
                    map.put(selectedField.getName(), eval.get(selectedField.getName()));
                }
                ret.add(map);
            }
        }
        return ret;
    }
}
