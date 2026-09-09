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

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.db.Context;
import org.efaps.util.EFapsException;
import org.efaps.util.SignUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.UriBuilder;

@EFapsUUID("8dbeb851-0468-4612-95e5-170244120eee")
@EFapsApplication("eFaps-GraphQL")
public abstract class AbstractFileMutation
    extends AbstractMutation
{

    private static final Logger LOG = LoggerFactory.getLogger(AbstractFileMutation.class);

    public String evalUrl(final String reference)
        throws EFapsException
    {
        final var map = new HashMap<String, Object>();
        map.put("ref", reference);
        return evalUrl(map);
    }

    public String evalUrl(final Map<String, Object> queryParameters)
        throws EFapsException
    {
        if (!queryParameters.containsKey("expires")) {
            final var expires = Instant.now().plus(15, ChronoUnit.MINUTES).getEpochSecond();
            queryParameters.put("expires", expires);
        }

        if (!queryParameters.containsKey("ctxU")) {
            queryParameters.put("ctxU", Context.getThreadContext().getPerson().getUUID());
        }

        if (!queryParameters.containsKey("ctxC")) {
            queryParameters.put("ctxC", Context.getThreadContext().getCompany().getUUID());
        }
        String signature = null;
        try {
            signature = queryParameters.containsKey("ref")
                            ? SignUtil.sign(queryParameters.get("expires"),
                                            queryParameters.get("ctxU"),
                                            queryParameters.get("ctxC"),
                                            queryParameters.get("ref"))
                            : SignUtil.sign(queryParameters.get("expires"),
                                            queryParameters.get("ctxU"),
                                            queryParameters.get("ctxC"));
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            LOG.error("catched", e);
        }
        queryParameters.put("signature", signature);

        final var bldr = UriBuilder.fromPath("/api/signed-upload");
        for (final var entry : queryParameters.entrySet()) {
            bldr.queryParam(entry.getKey(), entry.getValue());
        }
        return bldr.build().toString();
    }
}
