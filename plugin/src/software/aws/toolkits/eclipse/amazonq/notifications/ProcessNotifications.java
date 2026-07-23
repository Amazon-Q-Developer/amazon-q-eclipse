// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.PlatformUI;

import software.aws.toolkits.eclipse.amazonq.notifications.AmazonQNotificationPopup.NotificationAction;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.LocalizedContent;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationScheduleType;
import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationSeverity;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;

/**
 * Filters a fetched notifications payload and shows the survivors. Runs on the poll (worker) thread: it snapshots the
 * system/auth state once, applies STARTUP-once + dismissal + rules + in-session dedup, then marshals each surviving
 * toast onto the SWT UI thread. STARTUP notifications show only on the first poll of a session; an undismissed EMERGENCY
 * shows once per session (guarded by an in-memory set) rather than re-toasting on every poll.
 */
public final class ProcessNotifications {

    /**
     * Renders a notification that has passed all filtering. Injectable so tests can observe without SWT.
     * The {@code completion} consumer must be invoked with {@code true} once the toast has actually rendered
     * (so we only then commit telemetry + consume the startup window) or {@code false} if rendering was skipped
     * or failed (so the notification can be retried on a later poll).
     */
    public interface NotificationDisplay {
        void show(String id, NotificationData notification, LocalizedContent content,
                List<NotificationAction> actions, java.util.function.Consumer<Boolean> completion);
    }

    private final AtomicBoolean startupWindowOpen = new AtomicBoolean(true);
    private final Set<String> shownThisSession = ConcurrentHashMap.newKeySet();
    private final NotificationDismissalStore dismissalStore;
    private final NotificationDisplay display;

    public ProcessNotifications(final NotificationDismissalStore dismissalStore) {
        this(dismissalStore, ProcessNotifications::showToast);
    }

    public ProcessNotifications(final NotificationDismissalStore dismissalStore, final NotificationDisplay display) {
        this.dismissalStore = dismissalStore;
        this.display = display;
    }

    public void process(final NotificationsList list) {
        if (list == null || list.notifications() == null || list.notifications().isEmpty()) {
            return;
        }
        // Whether STARTUP notifications are still eligible this session. Consumed only once a STARTUP notification
        // actually survives all filters and is displayed (see processOne) — NOT merely because the first poll ran —
        // so a STARTUP item that is dismissed/rule-filtered/blank on the first poll can still show on a later poll
        // in the same session once it qualifies.
        final boolean startupEligible = startupWindowOpen.get();
        final SystemDetails sys = SystemDetailsCollector.collect();

        for (final NotificationData notification : list.notifications()) {
            if (notification == null) {
                continue;
            }
            try {
                processOne(notification, startupEligible, sys);
            } catch (Exception e) {
                Activator.getLogger().warn("Skipping notification that failed to process: " + notification.id(), e);
            }
        }
    }

    private void processOne(final NotificationData notification, final boolean startupEligible,
            final SystemDetails sys) {
        final String id = notification.id();
        if (id == null) {
            return;
        }
        final boolean isStartup = notification.schedule() != null
                && notification.schedule().type() == NotificationScheduleType.STARTUP;
        if (isStartup && !startupEligible) {
            return;
        }
        if (dismissalStore.isDismissed(id)) {
            return;
        }
        if (!RulesEngine.displayNotification(notification, sys)) {
            return;
        }
        if (notification.content() == null || notification.content().enUs() == null) {
            Activator.getLogger().info("Skipping notification with no en-US content: " + id);
            return;
        }
        final LocalizedContent content = notification.content().enUs();
        if (isBlank(content.title()) || isBlank(content.description())) {
            Activator.getLogger().info("Skipping notification with blank title/description: " + id);
            return;
        }
        if (!shownThisSession.add(id)) {
            return;
        }
        final List<NotificationAction> actions = new ArrayList<>(NotificationActionFactory.createActions(
                id, notification.actions(), content.title(), content.description()));
        // The explicit "Dismiss" button persists the dismissal so the notification does not reappear;
        // closing/auto-fading or clicking another action does NOT dismiss (an emergency re-shows next session).
        actions.add(new NotificationAction("Dismiss", () -> dismissalStore.dismiss(id)));
        final boolean isStartupNotification = isStartup;
        // Telemetry + startup-window consumption are committed only after the toast actually renders (completion
        // == true). If rendering is skipped/failed, un-mark it so a later poll can retry.
        display.show(id, notification, content, actions, rendered -> {
            if (Boolean.TRUE.equals(rendered)) {
                NotificationTelemetryProvider.emitShowNotification(id);
                if (isStartupNotification) {
                    startupWindowOpen.set(false);
                }
            } else {
                shownThisSession.remove(id);
            }
        });
    }

    private static void showToast(final String id, final NotificationData notification, final LocalizedContent content,
            final List<NotificationAction> actions, final java.util.function.Consumer<Boolean> completion) {
        final NotificationSeverity severity = NotificationSeverity.fromString(notification.severity());
        Activator.getLogger().info("Showing notification toast: " + id + " (severity=" + severity + ")");
        Display.getDefault().asyncExec(() -> {
            if (!PlatformUI.isWorkbenchRunning()) {
                Activator.getLogger().info("Workbench not running; skipping notification toast: " + id);
                completion.accept(false);
                return;
            }
            try {
                new AmazonQNotificationPopup(Display.getCurrent(), content.title(), content.description(), severity,
                        actions).open();
                completion.accept(true);
            } catch (Exception e) {
                Activator.getLogger().error("Failed to render notification toast: " + id, e);
                completion.accept(false);
            }
        });
    }

    private static boolean isBlank(final String s) {
        return s == null || s.isBlank();
    }
}
