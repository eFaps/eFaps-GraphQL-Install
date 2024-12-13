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

import org.efaps.admin.event.Parameter;
import org.efaps.admin.event.Return;
import org.efaps.admin.program.esjp.EFapsApplication;
import org.efaps.admin.program.esjp.EFapsListener;
import org.efaps.admin.program.esjp.EFapsUUID;
import org.efaps.esjp.admin.common.IReloadCacheListener;
import org.efaps.graphql.providers.EntryPointProvider;
import org.efaps.graphql.providers.MutationProvider;
import org.efaps.graphql.providers.TypeProvider;
import org.efaps.util.EFapsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@EFapsUUID("ff8e1700-acef-4504-8b0b-338d5afb8417")
@EFapsApplication("eFaps-GraphQL")
@EFapsListener
public class Caching
    implements IReloadCacheListener
{
    private static final Logger LOG = LoggerFactory.getLogger(Caching.class);

    @Override
    public int getWeight()
    {
        return 0;
    }

    @Override
    public void onReloadSystemConfig(final Parameter parameter)
        throws EFapsException
    {
        clearCache();
    }

    @Override
    public void onReloadCache(final Parameter parameter)
        throws EFapsException
    {
        clearCache();
    }

    public Return clearCache(final Parameter parameter)
    {
        clearCache();
        return new Return();
    }

    public void clearCache()
    {
        LOG.info("Clear cache for GraphQL");
        EntryPointProvider.clearCache();
        MutationProvider.clearCache();
        TypeProvider.clearCache();
    }
}
