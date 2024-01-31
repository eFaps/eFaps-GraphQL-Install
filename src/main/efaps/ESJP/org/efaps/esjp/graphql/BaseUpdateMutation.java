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

import org.efaps.admin.datamodel.AttributeSet;
import org.efaps.admin.datamodel.Classification;
import org.efaps.admin.datamodel.Type;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Instance;
import org.efaps.eql.EQL;
import org.efaps.eql.builder.Converter;
import org.efaps.eql.builder.Insert;
import org.efaps.eql.builder.Update;
import org.efaps.eql2.bldr.AbstractUpdateEQLBuilder;
import org.efaps.esjp.db.InstanceUtils;
import org.efaps.util.EFapsException;
import org.efaps.util.OIDUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;

@EFapsUUID("05f01272-afab-49a9-a8d2-9e763fe44db2")
@EFapsApplication("eFaps-GraphQL")
public class BaseUpdateMutation
    extends AbstractMutation
{

    private static final Logger LOG = LoggerFactory.getLogger(BaseUpdateMutation.class);

    @Override
    public Object get(final DataFetchingEnvironment environment)
        throws Exception
    {
        final var resultBldr = DataFetcherResult.newResult();
        final var props = getProperties(environment);
        final var instance = evalInstance(environment, props);
        if (InstanceUtils.isValid(instance)) {
            final var values = evalArgumentValues(environment, props);
            executeStmt(instance, values);
            resultBldr.data(instance.getOid());
        } else {
            resultBldr.error(GraphqlErrorBuilder.newError(environment)
                            .message("No valid instance could be evaluated")
                            .build());
        }
        return resultBldr.build();
    }

    protected Instance evalInstance(final DataFetchingEnvironment environment,
                                    final Properties props)
        throws EFapsException
    {
        Instance ret = null;
        LOG.info("Evaluating instance to update");
        final var instanceVariableName = props.getProperty("InstanceVariable", "oid");
        final var inputValue = environment.<String>getArgument(instanceVariableName);
        if (OIDUtil.isOID(inputValue)) {
            ret = Instance.get(inputValue);
            LOG.info("-> {}", ret);
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    protected Instance executeStmt(final Instance instance,
                                   final Map<String, Object> values)
        throws EFapsException
    {
        LOG.debug("Execute base stmt");
        final var eqlBldr = EQL.builder().update(instance);
        for (final var entry : values.entrySet()) {
            final var field = entry.getKey().trim();
            if (field.startsWith("attribute[")) {
                final String attrName = field.substring(10, field.length() - 1);
                eqlBldr.set(attrName, Converter.convert(entry.getValue()));
            }
            if (field.startsWith("linkto[")) {
                evalLinkto(instance.getType(), eqlBldr, entry.getValue(), field);
            }
        }
        eqlBldr.execute();
        for (final var entry : values.entrySet()) {
            final var field = entry.getKey().trim();
            if (field.startsWith("attributeset[")) {
                for (final var valuesEntry : (List<Map<String, Object>>) entry.getValue()) {
                    executeStmt(instance, field, valuesEntry);
                }
            }
            if (field.startsWith("class[")) {
                executeStmt(instance, field, (Map<String, Object>) entry.getValue());
            }
        }
        return instance;
    }

    protected Instance executeStmt(final Instance parentInstance,
                                   final String select,
                                   final Map<String, Object> values)
        throws EFapsException
    {

        Type type = null;
        AbstractUpdateEQLBuilder<?> eqlBldr = null;
        if (select.startsWith("attributeset[")) {
            final var attrName = select.substring(13, select.length() - 1);
            final var attrSet = AttributeSet.find(parentInstance.getType().getName(), attrName);
            final var deleteEval = EQL.builder().print().query(attrSet.getName())
                            .where()
                            .attr(attrName).eq(parentInstance)
                            .select()
                            .oid()
                            .evaluate();
            while (deleteEval.next()) {
                EQL.builder().delete(deleteEval.inst()).stmt().execute();
            }
            eqlBldr = EQL.builder().insert(attrSet)
                            .set(attrName, Converter.convert(parentInstance));
            type = attrSet;
        } else if (select.startsWith("class[")) {
            final var classTypeName = select.substring(6, select.length() - 1);
            final var classification = Classification.get(classTypeName);
            type = classification;

            final var check4Classification = EQL.builder().print()
                            .query(classification.getClassifyRelationType().getName())
                            .where()
                            .attr(classification.getRelLinkAttributeName()).eq(parentInstance)
                            .and()
                            .attr(classification.getRelTypeAttributeName()).eq(classification.getId())
                            .select()
                            .oid()
                            .evaluate();
            if (check4Classification.next()) {
                final var classEval = EQL.builder().print().query(classification.getName())
                                .where()
                                .attr(classification.getLinkAttributeName()).eq(parentInstance)
                                .select()
                                .oid()
                                .evaluate();
                classEval.next();
                eqlBldr = EQL.builder().update(classEval.inst());
            } else {
                EQL.builder().insert(classification.getClassifyRelationType())
                                .set(classification.getRelLinkAttributeName(), Converter.convert(parentInstance))
                                .set(classification.getRelTypeAttributeName(),
                                                Converter.convert(classification.getId()))
                                .execute();
                eqlBldr = EQL.builder().insert(classification)
                                .set(classification.getLinkAttributeName(), Converter.convert(parentInstance));
            }
        }

        for (final var entry : values.entrySet()) {
            final var field = entry.getKey().trim();
            if (field.startsWith("attribute[")) {
                final String attrName = field.substring(10, field.length() - 1);
                eqlBldr.set(attrName, Converter.convert(entry.getValue()));
            }
            if (field.startsWith("linkto[")) {
                evalLinkto(type, eqlBldr, entry.getValue(), field);
            }
        }
        return eqlBldr instanceof Update ? parentInstance : ((Insert) eqlBldr).execute();
    }
}
