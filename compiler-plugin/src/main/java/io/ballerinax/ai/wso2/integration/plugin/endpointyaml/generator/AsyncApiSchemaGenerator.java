/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.ballerinax.ai.wso2.integration.plugin.endpointyaml.generator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Builds the AsyncAPI schema describing a voice-agent service's fixed {@code ChatMessage} /
 * {@code onChatMessage} contract (defined in {@code ballerina/voice-types.bal}), by filling in a
 * bundled AsyncAPI YAML template. The contract is stable and statically known, so a template is
 * used instead of a full service-to-spec mapper library.
 */
final class AsyncApiSchemaGenerator {

    private static final String TEMPLATE_RESOURCE = "asyncapi/voice-agent-asyncapi-template.yaml";
    private static final String TEMPLATE = loadTemplate();

    private AsyncApiSchemaGenerator() {
    }

    static String generate(String name, String basePath, int port) {
        String channel = basePath.isEmpty() || basePath.startsWith("/") ? basePath : "/" + basePath;
        if (channel.isEmpty()) {
            channel = "/";
        }
        return TEMPLATE
                .replace("${title}", name)
                .replace("${port}", Integer.toString(port))
                .replace("${channel}", channel);
    }

    private static String loadTemplate() {
        try (InputStream inputStream =
                AsyncApiSchemaGenerator.class.getClassLoader().getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing bundled resource: " + TEMPLATE_RESOURCE);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load AsyncAPI template: " + TEMPLATE_RESOURCE, e);
        }
    }
}
