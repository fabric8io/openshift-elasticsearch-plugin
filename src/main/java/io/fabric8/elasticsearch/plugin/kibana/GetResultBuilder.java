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

package io.fabric8.elasticsearch.plugin.kibana;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.ObjectUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.index.get.GetField;
import org.elasticsearch.index.get.GetResult;

/**
 * Builder to properly build results for requests
 * modified to the kibana index
 */
public class GetResultBuilder {
    
    private static final Logger LOG = Loggers.getLogger(GetResultBuilder.class);
    private String index;
    private String replacedIndex;
    private String type = "";
    private String id = "";
    private long version;
    private boolean exists;
    private GetResponse response;
    private Map<String, GetField> responseFields = new HashMap<>(); 

    public GetResultBuilder index(String index) {
        this.index = index;
        return this;
    }

    public GetResultBuilder replacedIndex(String index) {
        this.replacedIndex = index;
        return this;
    }

    @SuppressWarnings("unchecked")
    public GetResultBuilder response(GetResponse response) {
        this.response = response;
        if(this.response != null) {
            type = (String) ObjectUtils.defaultIfNull(response.getType(), "");
            id = (String) ObjectUtils.defaultIfNull(response.getId(), "");
            version = (long) ObjectUtils.defaultIfNull(response.getVersion(), 0L);
            exists = response.isExists();
            responseFields = (Map<String, GetField>) ObjectUtils.defaultIfNull(response.getFields(), new HashMap<String, GetField>());
        }
        return this;
    }
    
    public GetResult build() {
        // Check for .kibana.* in the source
        BytesReference replacedContent = null;
        if (response != null && !response.isSourceEmpty() && replacedIndex != null && index != null) {
            String source = response.getSourceAsBytesRef().utf8ToString();
            String replaced = source.replaceAll(replacedIndex, index);
            replacedContent = new BytesArray(replaced);
        }
        // Check for .kibana.* in the fields
        for (String key : responseFields.keySet()) {

            GetField replacedField = responseFields.get(key);

            for (Object o : replacedField.getValues()) {
                if (o instanceof String) {
                    String value = (String) o;

                    if (value.contains(replacedIndex)) {
                        replacedField.getValues().remove(o);
                        replacedField.getValues().add(value.replaceAll(replacedIndex, index));
                    }
                }
            }

        }
        GetResult result = new GetResult(index, type, id, version, exists, replacedContent, responseFields);
        LOG.debug("Built GetResult: {}", result);
        return result;
    }
    
}
