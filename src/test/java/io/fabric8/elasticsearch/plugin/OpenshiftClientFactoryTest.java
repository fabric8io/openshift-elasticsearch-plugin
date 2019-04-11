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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.google.common.io.Files;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.openshift.client.NamespacedOpenShiftClient;

public class OpenshiftClientFactoryTest {

    private final String cert =
        "-----BEGIN CERTIFICATE-----\n"
            + "MIIC0DCCAbigAwIBAgIBATANBgkqhkiG9w0BAQsFADAZMRcwFQYDVQQDEw5sb2dn\n"
            +  "aW5nLXNpZ25lcjAeFw0xNzEwMTQxMTMwMDFaFw0yNzEwMTIxMTMwMDJaMBkxFzAV\n"
            +  "BgNVBAMTDmxvZ2dpbmctc2lnbmVyMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIB\n"
            +  "CgKCAQEAwjq3zygMEywx0PD/qGO2IwZxN18DwJbWB71JH+ldbLQHMJ3fvIy4wpJV\n"
            +  "FlJDPAejQ6hFnsoArVZInxcIcVfTiLgX15CXfCcrWUXXxfY2WWc6qDbQKje/+VZX\n"
            +  "/nr8c5DvbiDQxTDjXNO7WDGxqCaJIKg72VIqE4ac4AYNEwHeW3rd5cLEh/wfAu3n\n"
            +  "/iGFQ0v75ZG8ef2QQE364/d5GHMrXcWXUrXxuqRO/wdEjuXkP3SY/8sUZHCdugt8\n"
            +  "QygSXLp5mHaMc+Ie70/gl7u8wAxJGOvkjYVEgZPUTbemjEYhr9QwMuPvXxalWNc8\n"
            +  "kWIsOXnnyKG+RWDo7FE7kZtXpYfBcwIDAQABoyMwITAOBgNVHQ8BAf8EBAMCAqQw\n"
            +  "DwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAf+1IaNvSYQ1BNQVa\n"
            +  "hODEru6x+Ytg5HUyykT4tmxvvlqLS03ez37zKi2tDQBI/Sl4l46mu9H7GS98viO7\n"
            +  "Cj9Vn7km70GH6vDvCjY3iMKYK+rXzp1D2az0wmdmYymfrP8WC4X0q+KMZKPSVb9g\n"
            +  "9/0kAKPtH7YRzTiaSMlWhxNFQxM+zrHvw/Vp16PXZwq+FCbtv6zemQKo4JBHN2LM\n"
            +  "dIfgLqEMBkpvo1TeD3HOB4LyJJ6nnG4bUWsOnYYSZw1L70rHX9Vu5xq7xap2eL9g\n"
            +  "Uk4XsS8F+8hOE3zaHbqKbxRSqxnNqBI+UM+nQc1i3Qh2CXy8jgdVTjxWstDN/IHN\n"
            +  "Y6RrKw==                                                        \n"
            +  "-----END CERTIFICATE-----";

    // We need a temporary folder because K8S plugin check if CA file exists in constructor
    @Rule
    public TemporaryFolder certFolder = new TemporaryFolder();

    @Test
    public void testWhenRemoteCAIsNotNull() throws Exception {

        File tempCaFile = certFolder.newFile("ca.crt");
        Files.write(cert.getBytes(), tempCaFile);

        final Config config = (new ConfigBuilder()).build();
        final PluginSettings pluginSettings = mock(PluginSettings.class);
        when(pluginSettings.getOpenshiftCaPath()).thenReturn(tempCaFile.getAbsolutePath());
        when(pluginSettings.getMasterUrl()).thenReturn("https://foo.bar");
        when(pluginSettings.isTrustCerts()).thenReturn(false);

        OpenshiftAPIService.OpenShiftClientFactory factory = new OpenshiftAPIService.OpenShiftClientFactory(){};

        final NamespacedOpenShiftClient openShiftClient = factory.buildClient(pluginSettings, "foo");
        final Config k8sConfig = openShiftClient.getConfiguration();

        assertEquals("Exp. the CA file path to be loaded by K8S plugin", tempCaFile.getAbsolutePath(), k8sConfig.getCaCertFile());
        assertEquals("Exp. the master url to be correctly set in the K8S plugin", "https://foo.bar/", k8sConfig.getMasterUrl());
        assertFalse("Exp. the trust cert boolean to be correctly set in the K8S plugin", k8sConfig.isTrustCerts());
    }

}
