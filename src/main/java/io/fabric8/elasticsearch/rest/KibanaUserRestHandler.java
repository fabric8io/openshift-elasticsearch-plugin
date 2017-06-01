/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fabric8.elasticsearch.rest;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.KibanaUserReindexFilter;
import io.fabric8.elasticsearch.util.RequestUtils;

public class KibanaUserRestHandler extends BaseRestHandler implements ConfigurationSettings {

    private final ESLogger logger;

    @Inject
    public KibanaUserRestHandler(Settings settings, RestController controller, Client client, RequestUtils utils) {
        super(settings, controller, client);
        this.logger = Loggers.getLogger(KibanaUserRestHandler.class);

        boolean reindexEnabled = settings.getAsBoolean(OPENSHIFT_KIBANA_REWRITE_ENABLED_FLAG,
                OPENSHIFT_KIBANA_REWRITE_ENABLED_DEFAULT);
        logger.debug("Starting with Kibana reindexing feature enabled: {}", reindexEnabled);

        if (reindexEnabled) {
            controller.registerFilter(new KibanaUserReindexFilter(settings, logger, utils));
        }
    }

    @Override
    public void handleRequest(RestRequest request, RestChannel channel, Client client) throws Exception {
        return;
    }
}
