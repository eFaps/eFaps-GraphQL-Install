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

import java.util.Map;
import java.util.Objects;

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.eql.EQL;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.util.EFapsException;
import org.efaps.util.OIDUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.execution.DataFetcherResult.Builder;
import graphql.schema.DataFetchingEnvironment;

@EFapsUUID("8fc3f9d2-2b5e-4d34-9718-cdc84ba83ca8")
@EFapsApplication("eFaps-GraphQL")
public class BaseDeleteMutation
    extends AbstractMutation
{

    private static final Logger LOG = LoggerFactory.getLogger(BaseDeleteMutation.class);

    @Override
    public Object get(final DataFetchingEnvironment environment)
        throws Exception
    {
        final var resultBldr = DataFetcherResult.newResult();
        final var props = getProperties(environment);
        final var argumentValues = evalArgumentValues(environment, props);
        final var instance = evalInstance(environment, argumentValues);
        if (InstanceUtils.isValid(instance)) {
            executeStmt(environment, resultBldr, instance);
        } else {
            resultBldr.error(GraphqlErrorBuilder.newError(environment)
                            .message("No valid instance for deletion could be evaluated")
                            .build());
        }
        return resultBldr.build();
    }

    protected Instance evalInstance(final DataFetchingEnvironment environment,
                                    final Map<String, Object> argumentValues)
        throws EFapsException
    {
        Instance ret = null;
        LOG.info("Evaluating instance to be deleted");
        final var inputValue = Objects.toString(argumentValues.get("oid"));
        if (OIDUtil.isOID(inputValue)) {
            ret = Instance.get(inputValue);
        }
        return ret;
    }

    protected Instance executeStmt(final DataFetchingEnvironment environment,
                                   final Builder<Object> resultBldr,
                                   final Instance instance)
        throws EFapsException
    {
        LOG.debug("Execute base delete stmt");
        EQL.builder().delete(instance).stmt().execute();
        return instance;
    }
}
