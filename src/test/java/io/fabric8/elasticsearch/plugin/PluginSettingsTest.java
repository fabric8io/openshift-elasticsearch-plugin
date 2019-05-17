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

package io.fabric8.elasticsearch.plugin;

import static org.junit.Assert.assertEquals;

import org.elasticsearch.common.settings.Settings;
import org.junit.Test;

public class PluginSettingsTest {

    private Settings settings = Settings.EMPTY;

    @Test
    public void testRoleStrategyDefault() {
        PluginSettings plugin = new PluginSettings(settings);
        assertEquals("Exp. the plugin default to make roles based on users", "user", plugin.getRoleStrategy());
    }

    @Test
    public void testKibanaIndexModeDefault() {
        PluginSettings plugin = new PluginSettings(settings);
        assertEquals("Exp. the plugin default to make kibana index mode unique", "unique", plugin.getKibanaIndexMode());
    }

    @Test
    public void testGetACLExpiresInMillis() {
        PluginSettings plugin = new PluginSettings(settings);
        assertEquals("Exp. the plugin default to make kibana index mode unique", 
                ConfigurationSettings.DEFAULT_OPENSHIFT_CONTEXT_CACHE_EXPIRE_SECONDS * 1000, plugin.getACLExpiresInMillis());
    }

}
