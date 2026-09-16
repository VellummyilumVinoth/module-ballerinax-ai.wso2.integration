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

package io.ballerinax.ai.wso2.integration.plugin;

import io.ballerina.projects.plugins.CompilerPlugin;
import io.ballerina.projects.plugins.CompilerPluginContext;
import io.ballerinax.ai.wso2.integration.plugin.endpointyaml.generator.Endpoint;
import io.ballerinax.ai.wso2.integration.plugin.endpointyaml.generator.EndpointMetadataLifecycleListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/**
 * Compiler plugin for the ai.wso2.integration connector. When a project declares
 * {@code service on <CloudVoiceListener>} and is built with {@code bal build --export-endpoints},
 * publishes each service's endpoint metadata via
 * {@code io.ballerina.projects.plugins.EndpointMetaInfo}.
 */
public class AiWso2IntegrationCompilerPlugin extends CompilerPlugin {

    @Override
    public void init(CompilerPluginContext context) {
        Map<String, Object> ctxData = context.userData();
        ctxData.put(PluginConstants.CTX_DATA_ENDPOINTS, Collections.synchronizedList(new ArrayList<Endpoint>()));
        context.addCodeAnalyzer(new AiWso2IntegrationCodeAnalyzer(ctxData));
        context.addCompilerLifecycleListener(new EndpointMetadataLifecycleListener(ctxData));
    }
}
