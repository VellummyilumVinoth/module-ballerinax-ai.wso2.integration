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

import io.ballerina.compiler.syntax.tree.SyntaxTree;

/**
 * Derives the AsyncAPI schema file name for a voice-agent service, following the same convention
 * {@code module-ballerina-http}'s compiler plugin uses for its per-service OpenAPI files: the
 * source file's path (slashes flattened to underscores, {@code .bal} stripped) plus the service's
 * base path, or a disambiguator when the base path is empty.
 */
final class SchemaFileNameGenerator {

    private static final String SLASH = "/";
    private static final String UNDERSCORE = "_";
    private static final String BAL_EXTENSION = ".bal";
    private static final String SUFFIX = "_asyncapi.yaml";

    private SchemaFileNameGenerator() {
    }

    static String generateFileName(SyntaxTree syntaxTree, String basePath, int disambiguator) {
        String filePath = syntaxTree.filePath().replace(SLASH, UNDERSCORE);
        String balFileName = filePath.endsWith(BAL_EXTENSION)
                ? filePath.substring(0, filePath.length() - BAL_EXTENSION.length()) : filePath;
        String normalizedBasePath = normalizeBasePath(basePath);
        if (normalizedBasePath.isEmpty()) {
            return balFileName + UNDERSCORE + disambiguator + SUFFIX;
        }
        return balFileName + UNDERSCORE + normalizedBasePath + SUFFIX;
    }

    private static String normalizeBasePath(String basePath) {
        String trimmed = basePath.startsWith(SLASH) ? basePath.substring(1) : basePath;
        return trimmed.replace(SLASH, UNDERSCORE);
    }
}
