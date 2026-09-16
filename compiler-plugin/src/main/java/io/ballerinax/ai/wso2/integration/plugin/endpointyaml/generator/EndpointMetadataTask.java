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

import io.ballerina.projects.plugins.CompilerLifecycleEventContext;
import io.ballerina.projects.plugins.CompilerLifecycleTask;
import io.ballerinax.ai.wso2.integration.plugin.PluginConstants;
import io.ballerinax.ai.wso2.integration.plugin.PluginUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Publishes every {@link Endpoint} collected during code analysis into ballerina-lang's own
 * {@code --export-endpoints} channel, once for the whole compilation after code generation
 * completes.
 */
public class EndpointMetadataTask implements CompilerLifecycleTask<CompilerLifecycleEventContext> {

    private static final String ENDPOINT_META_INFO_CLASS = "io.ballerina.projects.plugins.EndpointMetaInfo";
    private static final String ADD_ENDPOINT_METADATA_METHOD = "addEndpointMetadata";

    private final Map<String, Object> ctxData;

    public EndpointMetadataTask(Map<String, Object> ctxData) {
        this.ctxData = ctxData;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void perform(CompilerLifecycleEventContext context) {
        List<Endpoint> endpoints = (List<Endpoint>) ctxData.get(PluginConstants.CTX_DATA_ENDPOINTS);
        if (endpoints == null || endpoints.isEmpty()) {
            return;
        }
        try {
            for (Endpoint endpoint : endpoints) {
                addEndpointMetadata(context, endpoint);
            }
        } catch (ReflectiveOperationException | SecurityException e) {
            context.reportDiagnostic(PluginUtils.getDiagnostic(
                    PluginConstants.DiagnosticCodes.ENDPOINT_METADATA_PUBLISH_FAILED));
        }
    }

    private void addEndpointMetadata(CompilerLifecycleEventContext context, Endpoint endpoint)
            throws ReflectiveOperationException {
        Class<?> endpointMetaInfoClass = Class.forName(ENDPOINT_META_INFO_CLASS);
        Constructor<?> constructor = endpointMetaInfoClass.getConstructor(String.class, int.class, String.class,
                String.class, String.class);
        // EndpointMetaInfo's name is mandatory but unused by this plugin's own domain model; reusing
        // basePath (as module-ballerina-mcp's own EndpointMetadataTask does) satisfies the API without
        // tracking a separate name concept.
        Object endpointMetaInfo = constructor.newInstance(endpoint.getBasePath(), endpoint.getPort(),
                endpoint.getBasePath(), endpoint.getType(), endpoint.getSchemaPath());
        Method method = context.getClass().getMethod(ADD_ENDPOINT_METADATA_METHOD, endpointMetaInfoClass);
        method.setAccessible(true);
        method.invoke(context, endpointMetaInfo);
    }
}
