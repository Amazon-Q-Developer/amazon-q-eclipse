// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.telemetry;

import software.amazon.awssdk.services.toolkittelemetry.model.MetricDatum;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.telemetry.TelemetryDefinitions.Result;
import software.aws.toolkits.telemetry.ToolkitTelemetry;
import java.time.Instant;
import java.util.Set;

public final class ToolkitTelemetryProvider {
    public static final String LOGIN_MODULE = "login";

    private static final Set<String> NON_PASSIVE = Set.of("ellipsesMenu", "statusBar", "shortcut");

    private ToolkitTelemetryProvider() {
        //prevent instantiation
    }

    public static void emitExecuteCommandMetric(final ExecuteParams params) {
      var metadata = ToolkitTelemetry.ExecuteEvent()
              .command(params.command())
              .duration(params.duration())
              .result(params.result())
              .reason(params.reason())
              .passive(false)
              .createTime(Instant.now())
              .value(1.0)
              .build();
      Activator.getTelemetryService().emitMetric(metadata);
    }

    public static void emitOpenModuleEventMetric(final String module, final String source, final String failureReason) {
        Result result = Result.SUCCEEDED;
        boolean isPassive = (source != null && !NON_PASSIVE.contains(source));

        if (failureReason != null && !failureReason.equals("none")) {
            result = Result.FAILED;
            ToolkitTelemetry.OpenModuleEvent().reason(failureReason);
        }
        MetricDatum metadata = ToolkitTelemetry.OpenModuleEvent()
            .module(mapModuleId(module))
            .result(result)
            .source(source)
            .passive(isPassive)
            .createTime(Instant.now())
            .value(1.0)
            .build();
        Activator.getTelemetryService().emitMetric(metadata);
    }
    public static void emitCloseModuleEventMetric(final String module, final String failureReason) {
        Result result = (failureReason == null || failureReason.equals("none")) ? Result.SUCCEEDED : Result.FAILED;
        MetricDatum metadata = ToolkitTelemetry.CloseModuleEvent()
                .module(mapModuleId(module))
                .result(result)
                .passive(true)
                .createTime(Instant.now())
                .value(1.0)
                .build();
        Activator.getTelemetryService().emitMetric(metadata);
    }

    /**
     * Reports that a module finished loading, or failed to load. The module names are shared with the
     * other Amazon Q IDE plugins, so pass one of the constants declared on this class.
     *
     * @param module the module that finished loading
     * @param result whether the module loaded
     * @param reason a short reason code when the module failed to load, null otherwise
     */
    public static void emitDidLoadModuleEventMetric(final String module, final Result result, final String reason) {
        MetricDatum metadata = ToolkitTelemetry.DidLoadModuleEvent()
                .module(module)
                .result(result)
                .reason(reason)
                .passive(true)
                .createTime(Instant.now())
                .value(1.0)
                .build();
        Activator.getTelemetryService().emitMetric(metadata);
    }

    private static String mapModuleId(final String viewId) {
        String page = viewId.substring(viewId.lastIndexOf(".") + 1);
        switch (page) {
        case "ToolkitLoginWebview":
            return "AuthView";
        case "AmazonQChatWebview":
            return "ChatView";
        case "AmazonQCodeReferenceView":
            return "CodeReferenceView";
        default:
            return page;
        }
    }
    public record ExecuteParams(String command, double duration, Result result, String reason) { };
}
