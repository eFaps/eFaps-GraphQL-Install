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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.graphql.definition.ObjectDef;
import org.efaps.graphql.providers.DataFetcherProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLNamedSchemaElement;
import graphql.schema.GraphQLNamedType;

@EFapsUUID("c1511fdd-1a60-479f-81d5-ca5d7dd5bc88")
@EFapsApplication("eFaps-GraphQL")
public abstract class AbstractDataFetcher
    implements DataFetcher<Object>
{

    private static final Logger LOG = LoggerFactory.getLogger(AbstractDataFetcher.class);

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

    protected Map<String, String> getKeyMapping(final DataFetchingEnvironment environment,
                                                final GraphQLNamedType graphType,
                                                final String[] keys)
    {
        final var keyMapping = new HashMap<String, String>();
        final Optional<ObjectDef> objectDefOpt = environment.getGraphQlContext().getOrEmpty(graphType.getName());
        LOG.info("objectDefOpt {}", objectDefOpt);
        if (objectDefOpt.isPresent()) {
            final var objectDef = objectDefOpt.get();
            for (final var child : graphType.getChildren()) {
                final var fieldDef = objectDef.getFields().get(((GraphQLNamedSchemaElement) child).getName());
                if (fieldDef != null && StringUtils.isNotEmpty(fieldDef.getSelect())
                                && Arrays.stream(keys).anyMatch(fieldDef.getSelect()::equals)) {
                    keyMapping.put(fieldDef.getSelect(), fieldDef.getName());
                }
            }
        }
        return keyMapping;
    }

}
