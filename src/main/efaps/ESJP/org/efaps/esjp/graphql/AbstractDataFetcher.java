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

import java.util.HashMap;
import java.util.Properties;

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.graphql.providers.DataFetcherProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;

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



}
