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

import org.apache.commons.lang.StringEscapeUtils;

public class DocumentBuilder {

    private static final String SEARCH_FIELDS = "{\"properties\":{\"title\":{\"type\":\"string\"},\"description\":"
        + "{\"type\":\"string\"},\"hits\":{\"type\":\"integer\"},\"columns\":{\"type\":\"string\"},\"sort\":"
        + "{\"type\":\"string\"},\"version\":{\"type\":\"integer\"},\"kibanaSavedObjectMeta\":{\"properties\":"
        + "{\"searchSourceJSON\":{\"type\":\"string\"}}}}}";

    private StringBuffer contents;
    private boolean containsFields;

    public DocumentBuilder() {
        contents = new StringBuffer();
        containsFields = false;
        contents.append('{');
    }

    public DocumentBuilder title(String title) {
        return addField("title", title);
    }

    public DocumentBuilder description(String description) {
        return addField("description", description);
    }

    public DocumentBuilder version(int value) {
        return addField("version", value);
    }

    public DocumentBuilder timeFieldName(String name) {
        return addField("timeFieldName", name);
    }

    public DocumentBuilder intervalName(String name) {
        return addField("intervalName", name);
    }

    public DocumentBuilder searchProperty() {

        addComma();

        contents.append("\"properties\":");
        contents.append(StringEscapeUtils.escapeJava(SEARCH_FIELDS));

        if (!containsFields) {
            containsFields = true;
        }

        return this;
    }

    public DocumentBuilder defaultIndex(String index) {
        return addField(KibanaSeed.DEFAULT_INDEX_FIELD, index);
    }

    private DocumentBuilder addField(String key, int value) {

        addComma();

        contents.append('"');
        contents.append(key);
        contents.append("\":");
        contents.append(value);

        if (!containsFields) {
            containsFields = true;
        }

        return this;
    }

    private DocumentBuilder addField(String key, String value) {

        addComma();
        contents.append('"');
        contents.append(key);
        contents.append("\":\"");
        contents.append(value);
        contents.append('"');

        if (!containsFields) {
            containsFields = true;
        }

        return this;
    }

    private void addComma() {
        if (containsFields) {
            contents.append(',');
        }
    }

    public String build() {
        contents.append('}');
        return contents.toString();
    }
}
