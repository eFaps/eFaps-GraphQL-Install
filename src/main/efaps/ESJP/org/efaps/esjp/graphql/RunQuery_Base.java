/*
 * Copyright 2003 - 2022 The eFaps Team
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.event.Return.ReturnValues;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.esjp.ci.CIFormGraphQL;
import org.efaps.graphql.EFapsGraphQL;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EFapsUUID("52d0ef6c-eebc-4964-a0f6-73a9c247e08a")
@EFapsApplication("eFaps-GraphQL")
public abstract class RunQuery_Base
{

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(RunQuery.class);

    public Return execute(final Parameter _parameter)
        throws EFapsException
    {
        final Return ret = new Return();
        final StringBuilder html = new StringBuilder();
        html.append("document.getElementsByName('")
            .append(CIFormGraphQL.GraphQL_RunQueryForm.result.name)
            .append("')[0].innerHTML=\"")
            .append("<style> .eFapsForm .unlabeled .field { display: inline;} ")
            .append(" #result{ max-height: 400px; overflow: auto; width: 100%; background-color: lightgray; padding: 5px 10px;}")
            .append("</style><div id='result'>");

        try {
            String query = _parameter.getParameterValue(CIFormGraphQL.GraphQL_RunQueryForm.query.name);
            if (StringUtils.isEmpty(query)) {
                query = "{ __schema { types { name fields { name } } } }";
            }
            final var result = new EFapsGraphQL().query(query);
            LOG.info("{}", result);
            html.append(result);
        } catch (final Exception e) {
            LOG.error("Catched error:", e);
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final PrintStream ps = new PrintStream(baos);
            e.printStackTrace(ps);
            html.append(StringEscapeUtils.escapeEcmaScript(StringEscapeUtils.escapeHtml4(baos.toString())));
        } finally {
            html.append("</div>\";");
            ret.put(ReturnValues.SNIPLETT, html.toString());
        }
        return ret;
    }
}
