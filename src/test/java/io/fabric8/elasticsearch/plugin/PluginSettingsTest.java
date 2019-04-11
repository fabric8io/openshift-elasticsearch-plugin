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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
    public void testRemoteOpenshiftWithDefaultConfiguration() {
        PluginSettings plugin = new PluginSettings(Settings.builder().build());
        assertNull("Exp. remote Openshift URL is null by default to not override default K8S plugin behaviour", plugin.getMasterUrl());
        assertNull("Exp. Openshift certificate authority is null by default to not override default K8S plugin behaviour", plugin.getOpenshiftCaPath());
        assertNull("Exp. default trust cert is null to not override default K8S plugin behaviour", plugin.isTrustCerts());
    }

    @Test
    public void testRemoteOpenshift() {
        final String expectedRemoteOpenshiftUrl = "https://foo.bar:8443";
        final String expectedOpenshiftCaPath = "/etc/elasticsearch/secret/openshift-ca";
        final String source = "openshift.master.url: " + expectedRemoteOpenshiftUrl + "\n"
                            + "openshift.ca.path: " + expectedOpenshiftCaPath + "\n"
                            + "openshift.trust.certificates: false";
        PluginSettings plugin = new PluginSettings(Settings.builder().loadFromSource(source).build());
        assertEquals("Exp. the correct remote Openshift URL", expectedRemoteOpenshiftUrl, plugin.getMasterUrl());
        assertEquals("Exp. the correct Openshift certificate authority", expectedOpenshiftCaPath, plugin.getOpenshiftCaPath());
        assertFalse("Exp. the correct non default trust cert value from configuration", plugin.isTrustCerts());
    }

}
