// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.notifications;

import java.util.List;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;

import software.aws.toolkits.eclipse.amazonq.notifications.NotificationData.NotificationSeverity;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.util.ToolkitNotification;

/**
 * A toast notification that renders a severity icon, wrapped description, and N action buttons built from a hosted
 * notification's actions. INFO/WARNING keep the base auto-close timer; CRITICAL overrides {@link #scheduleAutoClose()}
 * to a no-op so it persists until the user dismisses it, ensuring critical alerts reach the user.
 */
public final class AmazonQNotificationPopup extends ToolkitNotification {

    /** A rendered action button: a label plus the handler to run when clicked. */
    public record NotificationAction(String label, Runnable onClick) { }

    private final String description;
    // Mylyn's default auto-close is 8s, which is too short to read a multi-line known-issue message.
    private static final long TRANSIENT_DELAY_CLOSE_MS = 20_000L;

    private final NotificationSeverity severity;
    private final boolean persistent;
    private final List<NotificationAction> actions;

    public AmazonQNotificationPopup(final Display display, final String title, final String description,
            final NotificationSeverity severity, final List<NotificationAction> actions) {
        super(display, title, description);
        this.description = description;
        this.severity = severity;
        this.persistent = severity == NotificationSeverity.CRITICAL;
        this.actions = actions == null ? List.of() : List.copyOf(actions);
        // CRITICAL persists until dismissed (delayClose = 0 => scheduleAutoClose is a no-op); others stay readable.
        final long delayClose = persistent ? 0L : TRANSIENT_DELAY_CLOSE_MS;
        setDelayClose(delayClose);
        Activator.getLogger().info("AmazonQNotificationPopup created: severity=" + severity
                + " persistent=" + persistent + " delayCloseMs=" + delayClose);
    }

    @Override
    protected void scheduleAutoClose() {
        // Belt-and-suspenders: never schedule an auto-close for a persistent (CRITICAL) notification.
        if (!persistent) {
            super.scheduleAutoClose();
        }
    }

    @Override
    protected void createContentArea(final Composite parent) {
        final Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(2, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        final Label iconLabel = new Label(container, SWT.NONE);
        final Image icon = createSeverityImage(severity);
        if (icon != null) {
            iconLabel.setImage(icon);
            // close() is final in the base class, so dispose the icon via a listener instead of overriding close().
            iconLabel.addDisposeListener(e -> {
                if (!icon.isDisposed()) {
                    icon.dispose();
                }
            });
        }
        iconLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));

        final Label messageLabel = new Label(container, SWT.WRAP);
        messageLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        messageLabel.setText(description != null ? description : "");

        if (!actions.isEmpty()) {
            createActionButtons(parent);
        }
    }

    private void createActionButtons(final Composite parent) {
        final Composite buttonRow = new Composite(parent, SWT.NONE);
        final GridLayout layout = new GridLayout(actions.size(), false);
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        buttonRow.setLayout(layout);
        buttonRow.setLayoutData(new GridData(SWT.END, SWT.CENTER, true, false));

        for (final NotificationAction action : actions) {
            final Button button = new Button(buttonRow, SWT.PUSH);
            button.setText(action.label());
            button.setLayoutData(new GridData(SWT.END, SWT.CENTER, false, false));
            button.addSelectionListener(new SelectionAdapter() {
                @Override
                public void widgetSelected(final SelectionEvent e) {
                    action.onClick().run();
                    close();
                }
            });
        }
    }

    private static Image createSeverityImage(final NotificationSeverity severity) {
        try {
            final ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
            final ImageDescriptor descriptor = sharedImages.getImageDescriptor(iconKey(severity));
            if (descriptor == null) {
                return null;
            }
            // createImage(false) returns null (rather than throwing) if the image can't be loaded.
            return descriptor.createImage(false);
        } catch (Exception e) {
            return null;
        }
    }

    static String iconKey(final NotificationSeverity severity) {
        switch (severity) {
            case CRITICAL:
                return ISharedImages.IMG_OBJS_ERROR_TSK;
            case WARNING:
                return ISharedImages.IMG_OBJS_WARN_TSK;
            case INFO:
            default:
                return ISharedImages.IMG_OBJS_INFO_TSK;
        }
    }
}
