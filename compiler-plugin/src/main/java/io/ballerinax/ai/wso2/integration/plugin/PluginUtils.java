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

import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.DiagnosticFactory;
import io.ballerina.tools.diagnostics.DiagnosticInfo;
import io.ballerina.tools.diagnostics.Location;
import io.ballerina.tools.text.LinePosition;
import io.ballerina.tools.text.LineRange;
import io.ballerina.tools.text.TextRange;

/**
 * Shared helpers for the ai.wso2.integration compiler plugin.
 */
public final class PluginUtils {

    /** Used for diagnostics not anchored to any source node, e.g. a whole-compilation artifact-write failure. */
    private static final Location NO_LOCATION = new Location() {
        private final LineRange lineRange = LineRange.from("", LinePosition.from(0, 0), LinePosition.from(0, 0));
        private final TextRange textRange = TextRange.from(0, 0);

        @Override
        public LineRange lineRange() {
            return lineRange;
        }

        @Override
        public TextRange textRange() {
            return textRange;
        }
    };

    private PluginUtils() {
    }

    public static boolean isAiWso2IntegrationModule(ModuleSymbol moduleSymbol) {
        return moduleSymbol.id().orgName().equals(PluginConstants.PACKAGE_ORG)
                && moduleSymbol.id().moduleName().equals(PluginConstants.PACKAGE_NAME);
    }

    public static Diagnostic getDiagnostic(PluginConstants.DiagnosticCodes diagnosticCode, Location location) {
        DiagnosticInfo diagnosticInfo = new DiagnosticInfo(diagnosticCode.code(), diagnosticCode.message(),
                diagnosticCode.severity());
        return DiagnosticFactory.createDiagnostic(diagnosticInfo, location);
    }

    public static Diagnostic getDiagnostic(PluginConstants.DiagnosticCodes diagnosticCode) {
        return getDiagnostic(diagnosticCode, NO_LOCATION);
    }
}
