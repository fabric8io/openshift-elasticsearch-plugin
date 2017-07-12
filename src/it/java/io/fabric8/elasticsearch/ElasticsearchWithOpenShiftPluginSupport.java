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

package io.fabric8.elasticsearch;

import java.util.Collection;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.test.ESIntegTestCase;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;

/**
 * This starts Elasticsearch nodes with installed OpenShift plugin
 * which also installs SearchGuard plugin due to transitive dependency.
 */
public class ElasticsearchWithOpenShiftPluginSupport extends ESIntegTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return pluginList(io.fabric8.elasticsearch.plugin.OpenShiftElasticSearchPlugin.class);
    }

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        Settings settings = Settings.builder()
            .put(super.nodeSettings(nodeOrdinal))
            //.put("path.conf", this.getDataPath("/config"))
            // set to false to completely disable Searchguard plugin functionality, this should result into failed tests?
            .put("searchguard.enabled", true)
            // Disabling ssl should fail, though it seems to be overridden somewhere...
            //.put(SSLConfigConstants.SEARCHGUARD_SSL_TRANSPORT_ENABLED, false)
            //.put("searchguard.ssl.http.enabled", false)
            .put(ConfigurationSettings.SG_CLIENT_KS_PATH, "src/test/resources/elasticsearch/config/admin.jks")
            .put(ConfigurationSettings.SG_CLIENT_TS_PATH, "src/test/resources/elasticsearch/config/logging-es.truststore.jks")
            .build();
        return settings;
    }
}
