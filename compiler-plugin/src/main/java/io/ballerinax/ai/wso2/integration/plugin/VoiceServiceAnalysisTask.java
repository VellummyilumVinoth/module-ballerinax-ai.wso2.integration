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

import io.ballerina.compiler.api.symbols.ServiceDeclarationSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.projects.BuildOptions;
import io.ballerina.projects.Project;
import io.ballerina.projects.plugins.AnalysisTask;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticSeverity;
import io.ballerinax.ai.wso2.integration.plugin.endpointyaml.generator.Endpoint;
import io.ballerinax.ai.wso2.integration.plugin.endpointyaml.generator.EndpointYamlGenerator;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Analysis task that, when {@code --export-endpoints} is set, collects every {@code service on
 * <CloudVoiceListener>} declaration's endpoint details for {@link
 * io.ballerinax.ai.wso2.integration.plugin.endpointyaml.generator.EndpointMetadataTask} to publish
 * once code generation completes.
 */
public class VoiceServiceAnalysisTask implements AnalysisTask<SyntaxNodeAnalysisContext> {

    private static final PrintStream outStream = System.out;

    private final Map<String, Object> ctxData;

    VoiceServiceAnalysisTask(Map<String, Object> ctxData) {
        this.ctxData = ctxData;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void perform(SyntaxNodeAnalysisContext context) {
        Project project = context.currentPackage().project();
        if (!isExportEndpoints(project.buildOptions())) {
            return;
        }
        for (Diagnostic diagnostic : context.semanticModel().diagnostics()) {
            if (diagnostic.diagnosticInfo().severity() == DiagnosticSeverity.ERROR) {
                // Don't collect endpoint data over a service declaration with pre-existing errors.
                return;
            }
        }

        ServiceDeclarationNode serviceNode = (ServiceDeclarationNode) context.node();
        Optional<Symbol> symbol = context.semanticModel().symbol(serviceNode);
        if (symbol.isEmpty() || !(symbol.get() instanceof ServiceDeclarationSymbol serviceDeclarationSymbol)) {
            return;
        }

        Optional<Endpoint> endpoint =
                new EndpointYamlGenerator(context, serviceNode).getEndpoint(serviceDeclarationSymbol);
        if (endpoint.isEmpty()) {
            return;
        }
        List<Endpoint> collected = (List<Endpoint>) ctxData.get(PluginConstants.CTX_DATA_ENDPOINTS);
        collected.add(endpoint.get());
    }

    private boolean isExportEndpoints(BuildOptions buildOptions) {
        try {
            return buildOptions.exportEndpoints();
        } catch (NoSuchMethodError e) {
            outStream.println(PluginConstants.DiagnosticCodes.OLD_DISTRO_NOT_SUPPORTED.message());
            return false;
        }
    }
}
