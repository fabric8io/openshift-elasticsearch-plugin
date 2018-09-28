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

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;

import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;

class OpenShiftRestResponse extends RestResponse {

    private final RestResponse response;
    private final BytesReference content;
    private static final Logger LOGGER = Loggers.getLogger(OpenShiftRestResponse.class);
    
    OpenShiftRestResponse(final RestResponse response, final OpenshiftRequestContext context, final String defaultKibanaIndex){
        this.response = response;
        this.content = evaluateContentForKibanaIndex(response.content(), context, defaultKibanaIndex);
    }
    
    @Override
    public String contentType() {
        return response.contentType();
    }

    @Override
    public BytesReference content() {
        return content;
    }

    @Override
    public RestStatus status() {
        return response.status();
    }

    @Override
    public void copyHeaders(ElasticsearchException ex) {
        response.copyHeaders(ex);
    }

    @Override
    public void addHeader(String name, String value) {
        response.addHeader(name, value);
    }

    @Override
    public Map<String, List<String>> getHeaders() {
        return response.getHeaders();
    }
    
    private BytesReference evaluateContentForKibanaIndex(BytesReference contentRef, OpenshiftRequestContext context, String defaultKibanaIndex) {
        if (context == null || context == OpenshiftRequestContext.EMPTY) {
            return contentRef;
        }
        String content = contentRef.utf8ToString();
        if(content.contains("_index\":\"" + context.getKibanaIndex())) {
            LOGGER.debug("Replacing the content that references the kibana index");
            String replaced = content.replaceAll("_index\":\"" + context.getKibanaIndex() + "\"", "_index\":\"" + defaultKibanaIndex + "\"");
            return new BytesArray(replaced);
        }
        return contentRef;
    }
}