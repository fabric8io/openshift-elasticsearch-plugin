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

package io.fabric8.elasticsearch.plugin.rest;

import java.io.IOException;

import org.elasticsearch.common.io.stream.BytesStreamOutput;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;

/**
 * Intercepts the response and modifies it if necessary
 */
public class RestChannelInterceptor implements RestChannel {

    private final RestChannel channel;
    private final ThreadContext threadContext;
    private final String defaultKibanaIndex;

    public RestChannelInterceptor(final RestChannel channel, ThreadContext threadContext, String defaultKibanaIndex) {
        this.channel = channel;
        this.threadContext = threadContext;
        this.defaultKibanaIndex = defaultKibanaIndex;
    }
    
    @Override
    public XContentBuilder newErrorBuilder() throws IOException {
        return channel.newErrorBuilder();
    }

    @Override
    public XContentBuilder newBuilder() throws IOException {
        return channel.newBuilder();
    }
    
    @Override
    public XContentBuilder newBuilder(XContentType contentType, boolean useFiltering) throws IOException {
        return channel.newBuilder(contentType, useFiltering);
    }

    @Override
    public BytesStreamOutput bytesOutput() {
        return channel.bytesOutput();
    }

    @Override
    public RestRequest request() {
        return channel.request();
    }

    @Override
    public boolean detailedErrorsEnabled() {
        return channel.detailedErrorsEnabled();
    }

    @Override
    public void sendResponse(RestResponse response) {
        OpenshiftRequestContext context = threadContext.getTransient(ConfigurationSettings.OPENSHIFT_REQUEST_CONTEXT);
        channel.sendResponse(new OpenShiftRestResponse(response, context, defaultKibanaIndex));
    }

}
