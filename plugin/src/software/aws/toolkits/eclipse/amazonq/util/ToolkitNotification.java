// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.util;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Monitor;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import java.util.concurrent.CopyOnWriteArrayList;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.mylyn.commons.ui.dialogs.AbstractNotificationPopup;

public class ToolkitNotification extends AbstractNotificationPopup {

    private final String title;
    private final String description;
    private Image infoIcon;
    private static CopyOnWriteArrayList<ToolkitNotification> activeNotifications = new CopyOnWriteArrayList<>();
    private static final int MAX_WIDTH = 400;
    private static final int MIN_HEIGHT = 100;
    private static final int PADDING_EDGE = 5;
    private static final int NOTIFICATIONS_GAP = 5;

    public ToolkitNotification(final Display display, final String title, final String description) {
        super(display);
        this.title = title;
        this.description = description;
    }

    private Image getInfoIcon() {
        ISharedImages sharedImages = PlatformUI.getWorkbench().getSharedImages();
        ImageDescriptor imageDescriptor = sharedImages.getImageDescriptor(ISharedImages.IMG_OBJS_INFO_TSK);
        this.infoIcon = imageDescriptor.createImage();
        return this.infoIcon;
    }

    /**
     * Creates the content area for the notification.
     * Subclasses may override this method to customize the notification content.
     *
     * @param parent the parent composite
     */
    @Override
    protected void createContentArea(final Composite parent) {
        Composite container = new Composite(parent, SWT.NONE);
        container.setLayout(new GridLayout(2, false));
        container.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));

        Label iconLabel = new Label(container, SWT.NONE);
        iconLabel.setImage(getInfoIcon());
        iconLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));

        Label notificationLabel = new Label(container, SWT.WRAP);
        notificationLabel.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, false));
        notificationLabel.setText(this.description);
    }

    @Override
    protected final String getPopupShellTitle() {
        return this.title;
    }

    @Override
    protected final void initializeBounds() {
        Rectangle clArea = getPrimaryClientArea();
        int width = Math.min(getShell().computeSize(SWT.DEFAULT, SWT.DEFAULT).x, MAX_WIDTH);
        // Recompute height with the constrained width so wrapped text and buttons are accounted for
        int height = Math.max(getShell().computeSize(width, SWT.DEFAULT).y, MIN_HEIGHT);
        Point size = new Point(width, height);
        // Calculate the position for the new notification. Floor x at the client-area left edge so the
        // shell can never be pushed off-screen to the right even if the width term is skewed (e.g. a DPI
        // scaling mismatch on high-DPI/multi-monitor Windows).
        int x = Math.max(clArea.x, clArea.x + clArea.width - size.x - PADDING_EDGE);
        int y = clArea.height + clArea.y - size.y - PADDING_EDGE;
        for (ToolkitNotification notification : activeNotifications) {
            if (!notification.getShell().isDisposed()) {
                y -= notification.getShell().getSize().y + NOTIFICATIONS_GAP;
            }
        }
        getShell().setLocation(x, y);
        getShell().setSize(size);
        activeNotifications.add(this);
    }

    private Rectangle getPrimaryClientArea() {
        Monitor primaryMonitor = getShell().getDisplay().getPrimaryMonitor();
        return primaryMonitor != null ? primaryMonitor.getClientArea() : getShell().getDisplay().getClientArea();
    }

    private void repositionNotifications() {
        Rectangle clArea = getPrimaryClientArea();
        // Right-align and stack each surviving notification by ITS OWN size. Previously a single x/y was
        // derived from the closing shell's width/height and applied to every survivor, so when a narrow
        // toast closed while a wider one remained, the wider toast was placed too far right and its right
        // edge hung off the screen. Mirror initializeBounds(): position each shell from its own getSize().
        int y = clArea.y + clArea.height - PADDING_EDGE;
        for (ToolkitNotification notification : activeNotifications) {
            if (!notification.getShell().isDisposed()) {
                Point size = notification.getShell().getSize();
                int x = Math.max(clArea.x, clArea.x + clArea.width - size.x - PADDING_EDGE);
                y -= size.y;
                notification.getShell().setLocation(x, y);
                y -= NOTIFICATIONS_GAP;
            }
        }
    }

    @Override
    public final boolean close() {
        activeNotifications.remove(this);
        repositionNotifications();
        if (this.infoIcon != null && !this.infoIcon.isDisposed()) {
            this.infoIcon.dispose();
        }
        return super.close();
    }
}
