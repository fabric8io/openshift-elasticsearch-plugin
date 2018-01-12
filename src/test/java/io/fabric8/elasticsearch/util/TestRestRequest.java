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

package io.fabric8.elasticsearch.util;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.rest.RestRequest;

public class TestRestRequest extends RestRequest {

    private static final String URI = "test/restrequest";

    @SuppressWarnings("unchecked")
    public TestRestRequest(Map<String, List<String>> headers) {
        super(NamedXContentRegistry.EMPTY, Collections.EMPTY_MAP, URI, headers);
    }

    @Override
    public Method method() {
        return Method.GET;
    }

    @Override
    public String uri() {
        return URI;
    }

    @Override
    public boolean hasContent() {
        return false;
    }

    @Override
    public BytesReference content() {
        return null;
    }

}
