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

import io.ballerina.compiler.api.symbols.FunctionTypeSymbol;
import io.ballerina.compiler.api.symbols.MethodSymbol;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.ParameterSymbol;
import io.ballerina.compiler.api.symbols.ServiceDeclarationSymbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.syntax.tree.BasicLiteralNode;
import io.ballerina.compiler.syntax.tree.BindingPatternNode;
import io.ballerina.compiler.syntax.tree.CaptureBindingPatternNode;
import io.ballerina.compiler.syntax.tree.CheckExpressionNode;
import io.ballerina.compiler.syntax.tree.ExplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.FunctionArgumentNode;
import io.ballerina.compiler.syntax.tree.ImplicitNewExpressionNode;
import io.ballerina.compiler.syntax.tree.ListenerDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModuleMemberDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.ModuleVariableDeclarationNode;
import io.ballerina.compiler.syntax.tree.NamedArgumentNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NodeList;
import io.ballerina.compiler.syntax.tree.ParenthesizedArgList;
import io.ballerina.compiler.syntax.tree.PositionalArgumentNode;
import io.ballerina.compiler.syntax.tree.ServiceDeclarationNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.projects.Document;
import io.ballerina.projects.DocumentId;
import io.ballerina.projects.Module;
import io.ballerina.projects.Project;
import io.ballerina.projects.plugins.SyntaxNodeAnalysisContext;
import io.ballerinax.ai.wso2.integration.plugin.PluginConstants;
import io.ballerinax.ai.wso2.integration.plugin.PluginUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Extracts an {@link Endpoint} (base path, port, and a sanity-checked {@code onChatMessage} handler)
 * from a {@code service on <CloudVoiceListener>} declaration.
 */
public final class EndpointYamlGenerator {

    /** Parameter names that carry the port (or an existing listener to borrow the port from). */
    private static final String LISTEN_ON_PARAM = "listenOn";
    private static final String PORT_PARAM = "port";
    /** Bounds the walk through chained listener variables/inline constructors, in case of a cycle. */
    private static final int MAX_LISTENER_RESOLUTION_DEPTH = 5;
    private static final String ENDPOINT_TYPE = "WS";
    /** Directory under the project's {@code target/} dir that generated schema files are written to. */
    private static final String ARTIFACT_DIR = "artifact";

    private final SyntaxNodeAnalysisContext context;
    private final ServiceDeclarationNode serviceNode;

    public EndpointYamlGenerator(SyntaxNodeAnalysisContext context, ServiceDeclarationNode serviceNode) {
        this.context = context;
        this.serviceNode = serviceNode;
    }

    /**
     * Returns the {@link Endpoint} for this service, or empty when it isn't a voice-listener
     * service, its port can't be statically resolved (a {@code PORT_RESOLUTION_FAILED} diagnostic is
     * reported in that case), or its {@code onChatMessage} handler doesn't match the expected shape.
     */
    public Optional<Endpoint> getEndpoint(ServiceDeclarationSymbol serviceDeclarationSymbol) {
        if (!isVoiceListenerService(serviceDeclarationSymbol)) {
            return Optional.empty();
        }

        String basePath = getBasePath();
        Integer port = resolvePort();
        if (port == null) {
            context.reportDiagnostic(PluginUtils.getDiagnostic(
                    PluginConstants.DiagnosticCodes.PORT_RESOLUTION_FAILED, serviceNode.location()));
            return Optional.empty();
        }

        // Defensive sanity check: only emit endpoint metadata when onChatMessage's shape is what we expect.
        if (!hasExpectedOnChatMessage(serviceDeclarationSymbol)) {
            return Optional.empty();
        }

        String schemaPath = writeSchema(basePath, port, serviceDeclarationSymbol);
        return Optional.of(new Endpoint(port, basePath, ENDPOINT_TYPE, schemaPath));
    }

    /**
     * Generates the AsyncAPI schema describing this service's {@code ChatMessage}/{@code
     * onChatMessage} contract and writes it to {@code target/artifact/}, returning just the
     * generated file name for use as {@link Endpoint#getSchemaPath()} -- matching the convention
     * {@code module-ballerina-http}'s compiler plugin uses for its own per-service OpenAPI files.
     * Returns an empty string, with a warning diagnostic, if the file can't be written.
     */
    private String writeSchema(String basePath, int port, ServiceDeclarationSymbol serviceDeclarationSymbol) {
        String fileName = SchemaFileNameGenerator.generateFileName(
                serviceNode.syntaxTree(), basePath, serviceDeclarationSymbol.hashCode());
        String title = basePath.isEmpty() ? "voice-agent" : basePath;
        String yaml = AsyncApiSchemaGenerator.generate(title, basePath, port);
        try {
            Project project = context.currentPackage().project();
            Path artifactDir = project.targetDir().resolve(ARTIFACT_DIR);
            Files.createDirectories(artifactDir);
            Files.writeString(artifactDir.resolve(fileName), yaml);
            return fileName;
        } catch (IOException e) {
            context.reportDiagnostic(PluginUtils.getDiagnostic(
                    PluginConstants.DiagnosticCodes.SCHEMA_WRITE_FAILED, serviceNode.location()));
            return "";
        }
    }

    private boolean isVoiceListenerService(ServiceDeclarationSymbol serviceDeclarationSymbol) {
        for (TypeSymbol listener : serviceDeclarationSymbol.listenerTypes()) {
            if (isVoiceListener(listener)) {
                return true;
            }
        }
        return false;
    }

    private boolean isVoiceListener(TypeSymbol listener) {
        if (listener.typeKind() == TypeDescKind.UNION) {
            for (TypeSymbol member : ((UnionTypeSymbol) listener).memberTypeDescriptors()) {
                if (isVoiceListener(member)) {
                    return true;
                }
            }
            return false;
        }
        Optional<ModuleSymbol> module = listener.getModule();
        String listenerName = listener.getName().orElse(null);
        return module.isPresent() && PluginUtils.isAiWso2IntegrationModule(module.get())
                && PluginConstants.LISTENER_NAME.equals(listenerName);
    }

    private String getBasePath() {
        StringBuilder basePath = new StringBuilder();
        NodeList<Node> resourcePath = serviceNode.absoluteResourcePath();
        for (Node identifierNode : resourcePath) {
            basePath.append(identifierNode.toString().replace("\"", "").trim());
        }
        return basePath.toString();
    }

    /**
     * Resolves the listener's port, following the {@code on <expr>} expression wherever it leads:
     * a listener variable (walked back to its own declaration), an inline {@code new(...)}/{@code
     * check new(...)} constructor call (matching a {@code listenOn}/{@code port} named argument, or
     * else the first positional argument), or an int literal / {@code configurable int}'s literal
     * default. Recurses through {@code listenOn = <anotherListener>}-style delegation until a port
     * is found. Returns {@code null} when it can't be statically resolved.
     */
    private Integer resolvePort() {
        SeparatedNodeList<ExpressionNode> listenerExpressions = serviceNode.expressions();
        if (listenerExpressions.isEmpty()) {
            return null;
        }
        Module module = context.currentPackage().module(context.moduleId());
        return resolvePortFromExpression(module, listenerExpressions.get(0), 0);
    }

    private Integer resolvePortFromExpression(Module module, ExpressionNode expression, int depth) {
        if (depth > MAX_LISTENER_RESOLUTION_DEPTH) {
            return null;
        }
        if (expression instanceof BasicLiteralNode basicLiteralNode
                && basicLiteralNode.kind() == SyntaxKind.NUMERIC_LITERAL) {
            return parseIntLiteral(basicLiteralNode);
        }
        if (expression instanceof SimpleNameReferenceNode nameReferenceNode) {
            String varName = nameReferenceNode.name().text().trim();

            Optional<ExpressionNode> listenerInitializer = findListenerInitializer(module, varName);
            if (listenerInitializer.isPresent()) {
                return resolvePortFromExpression(module, listenerInitializer.get(), depth + 1);
            }

            Optional<ModuleVariableDeclarationNode> configurableDecl = findModuleVariable(module, varName)
                    .filter(this::isConfigurable);
            if (configurableDecl.isPresent() && configurableDecl.get().initializer().isPresent()) {
                return resolvePortFromExpression(module, configurableDecl.get().initializer().get(), depth + 1);
            }
            return null;
        }

        Optional<ParenthesizedArgList> argList = getConstructorArgs(expression);
        if (argList.isEmpty() || argList.get().arguments().isEmpty()) {
            return null;
        }
        FunctionArgumentNode portArg = pickPortRelevantArgument(argList.get());
        ExpressionNode argExpression = portArg == null ? null : extractArgumentExpression(portArg);
        if (argExpression == null) {
            return null;
        }
        return resolvePortFromExpression(module, argExpression, depth + 1);
    }

    /**
     * Prefers a {@code listenOn}/{@code port} named argument (matching {@code
     * CloudVoiceListener.init}'s and {@code websocket:Listener.init}'s own parameter names) over
     * position, since a delegating call like {@code new(listenOn = wsListener)} may not pass it
     * first. Falls back to the first argument if it's positional.
     */
    private FunctionArgumentNode pickPortRelevantArgument(ParenthesizedArgList argList) {
        for (FunctionArgumentNode arg : argList.arguments()) {
            if (arg instanceof NamedArgumentNode namedArgumentNode) {
                String argName = namedArgumentNode.argumentName().name().text().trim();
                if (LISTEN_ON_PARAM.equals(argName) || PORT_PARAM.equals(argName)) {
                    return namedArgumentNode;
                }
            }
        }
        FunctionArgumentNode firstArg = argList.arguments().get(0);
        return firstArg instanceof PositionalArgumentNode ? firstArg : null;
    }

    private ExpressionNode extractArgumentExpression(FunctionArgumentNode arg) {
        if (arg instanceof PositionalArgumentNode positionalArgumentNode) {
            return positionalArgumentNode.expression();
        }
        if (arg instanceof NamedArgumentNode namedArgumentNode) {
            return namedArgumentNode.expression();
        }
        return null;
    }

    private Optional<ParenthesizedArgList> getConstructorArgs(ExpressionNode initializer) {
        ExpressionNode expression = initializer;
        if (expression instanceof CheckExpressionNode checkExpressionNode) {
            expression = checkExpressionNode.expression();
        }
        if (expression instanceof ExplicitNewExpressionNode explicitNewExpressionNode) {
            return Optional.of(explicitNewExpressionNode.parenthesizedArgList());
        }
        if (expression instanceof ImplicitNewExpressionNode implicitNewExpressionNode) {
            return implicitNewExpressionNode.parenthesizedArgList();
        }
        return Optional.empty();
    }

    private Integer parseIntLiteral(BasicLiteralNode basicLiteralNode) {
        try {
            return Integer.parseInt(basicLiteralNode.literalToken().text().trim().replace("_", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isConfigurable(ModuleVariableDeclarationNode node) {
        for (var qualifier : node.qualifiers()) {
            if (qualifier.kind() == SyntaxKind.CONFIGURABLE_KEYWORD) {
                return true;
            }
        }
        return false;
    }

    /**
     * Finds a {@code listener T v = ...;} declaration's own initializer expression. A listener
     * declaration is a distinct {@link ListenerDeclarationNode}, not a {@link
     * ModuleVariableDeclarationNode} -- unlike a plain module variable, it always carries an
     * initializer and names its variable directly via a {@code Token}, not a binding pattern.
     */
    private Optional<ExpressionNode> findListenerInitializer(Module module, String variableName) {
        for (DocumentId documentId : module.documentIds()) {
            Document document = module.document(documentId);
            Node rootNode = document.syntaxTree().rootNode();
            if (!(rootNode instanceof ModulePartNode modulePartNode)) {
                continue;
            }
            for (ModuleMemberDeclarationNode member : modulePartNode.members()) {
                if (member instanceof ListenerDeclarationNode listenerDeclarationNode
                        && listenerDeclarationNode.variableName().text().trim().equals(variableName)
                        && listenerDeclarationNode.initializer() instanceof ExpressionNode expressionNode) {
                    return Optional.of(expressionNode);
                }
            }
        }
        return Optional.empty();
    }

    private Optional<ModuleVariableDeclarationNode> findModuleVariable(Module module, String variableName) {
        for (DocumentId documentId : module.documentIds()) {
            Document document = module.document(documentId);
            Node rootNode = document.syntaxTree().rootNode();
            if (!(rootNode instanceof ModulePartNode modulePartNode)) {
                continue;
            }
            for (ModuleMemberDeclarationNode member : modulePartNode.members()) {
                if (!(member instanceof ModuleVariableDeclarationNode moduleVariableDeclarationNode)) {
                    continue;
                }
                BindingPatternNode bindingPattern =
                        moduleVariableDeclarationNode.typedBindingPattern().bindingPattern();
                if (bindingPattern instanceof CaptureBindingPatternNode captureBindingPatternNode
                        && captureBindingPatternNode.variableName().text().trim().equals(variableName)) {
                    return Optional.of(moduleVariableDeclarationNode);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Defensive check: confirms the service declares {@code onChatMessage} with a single
     * {@code ChatMessage}-typed parameter, so the endpoint metadata this plugin publishes actually
     * applies. The contract is fixed, not derived, per this connector's stable {@code
     * ChatMessage}/{@code onChatMessage} shape.
     */
    private boolean hasExpectedOnChatMessage(ServiceDeclarationSymbol serviceDeclarationSymbol) {
        Map<String, ? extends MethodSymbol> methods = serviceDeclarationSymbol.methods();
        MethodSymbol onChatMessage = methods.get(PluginConstants.ON_CHAT_MESSAGE);
        if (onChatMessage == null) {
            return false;
        }
        FunctionTypeSymbol functionType = onChatMessage.typeDescriptor();
        List<ParameterSymbol> params = functionType.params().orElse(List.of());
        if (params.size() != 1) {
            return false;
        }
        TypeSymbol paramType = params.get(0).typeDescriptor();
        String paramTypeName = paramType.getName().orElse(null);
        Optional<ModuleSymbol> paramModule = paramType.getModule();
        return PluginConstants.CHAT_MESSAGE_TYPE.equals(paramTypeName)
                && paramModule.isPresent() && PluginUtils.isAiWso2IntegrationModule(paramModule.get());
    }
}
