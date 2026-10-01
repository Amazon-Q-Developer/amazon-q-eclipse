// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.ViewPart;

import software.aws.toolkits.eclipse.amazonq.util.Constants;
import software.aws.toolkits.eclipse.amazonq.util.PluginUtils;

/**
 * Compact, titleless warning view positioned above the Eclipse editor area.
 */
public final class DeprecationBannerView extends ViewPart {

    public static final String ID = "software.aws.toolkits.eclipse.amazonq.views.DeprecationBannerView";

    private Composite parentComposite;

    @Override
    public void createPartControl(final Composite parent) {
        parentComposite = parent;
        if (!DeprecationBannerManager.getInstance().isPresentationActive()) {
            hideViewAsync(parent.getDisplay());
            return;
        }

        Color background = parent.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND);
        Color foreground = parent.getDisplay().getSystemColor(SWT.COLOR_INFO_FOREGROUND);
        parent.setBackground(background);
        parent.setLayout(new FillLayout());

        ScrolledComposite scrolled = new ScrolledComposite(parent, SWT.V_SCROLL);
        scrolled.setBackground(background);
        scrolled.setExpandHorizontal(true);
        scrolled.setExpandVertical(true);

        Composite content = new Composite(scrolled, SWT.NONE);
        configureContainer(content, background);

        Label warningIcon = new Label(content, SWT.NONE);
        warningIcon.setBackground(background);
        warningIcon.setImage(PlatformUI.getWorkbench().getSharedImages().getImage(ISharedImages.IMG_OBJS_WARN_TSK));
        warningIcon.setToolTipText("Warning");
        warningIcon.setLayoutData(new GridData(SWT.BEGINNING, SWT.BEGINNING, false, false));

        Composite textContainer = createTextContainer(content, background);
        createHeading(textContainer, background, foreground);
        createMessage(textContainer, background, foreground);
        createLearnMoreLink(textContainer, background);
        createDismissButton(content);

        scrolled.setContent(content);
        updateMinimumSize(scrolled, content);
        scrolled.addListener(SWT.Resize, event -> updateMinimumSize(scrolled, content));
    }

    private Composite createTextContainer(final Composite parent, final Color background) {
        Composite textContainer = new Composite(parent, SWT.NONE);
        textContainer.setBackground(background);
        GridLayout textLayout = new GridLayout(1, false);
        textLayout.marginWidth = 0;
        textLayout.marginHeight = 0;
        textLayout.verticalSpacing = 2;
        textContainer.setLayout(textLayout);
        textContainer.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        return textContainer;
    }

    private void createHeading(final Composite parent, final Color background,
            final Color foreground) {
        Label heading = new Label(parent, SWT.WRAP);
        heading.setBackground(background);
        heading.setForeground(foreground);
        heading.setText(Constants.DEPRECATION_NOTICE_TITLE);
        FontData[] fontData = heading.getFont().getFontData();
        for (FontData data : fontData) {
            data.setStyle(data.getStyle() | SWT.BOLD);
        }
        Font headingFont = new Font(parent.getDisplay(), fontData);
        heading.setFont(headingFont);
        heading.addDisposeListener(event -> headingFont.dispose());
        heading.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createMessage(final Composite parent, final Color background,
            final Color foreground) {
        Link message = new Link(parent, SWT.WRAP);
        message.setBackground(background);
        message.setForeground(foreground);
        message.setText(Constants.DEPRECATION_NOTICE_BODY.replace(
                "explore Kiro",
                String.format("<a href=\"%s\">explore Kiro</a>", Constants.KIRO_URL)));
        message.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        message.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent event) {
                PluginUtils.openWebpage(event.text);
            }
        });
    }

    private void createLearnMoreLink(final Composite parent, final Color background) {
        Link learnMore = new Link(parent, SWT.NONE);
        learnMore.setBackground(background);
        learnMore.setText(String.format("<a href=\"%s\">Learn more</a>",
                Constants.DEPRECATION_NOTICE_LEARN_MORE_URL));
        learnMore.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        learnMore.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent event) {
                PluginUtils.openWebpage(event.text);
            }
        });
    }

    private void createDismissButton(final Composite parent) {
        Button dismiss = new Button(parent, SWT.PUSH);
        dismiss.setText("Don't show again");
        dismiss.setToolTipText("Permanently dismiss this end-of-support banner");
        dismiss.setLayoutData(new GridData(SWT.END, SWT.BEGINNING, false, false));
        dismiss.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent event) {
                DeprecationBannerManager.getInstance().dismissPermanently();
            }
        });
    }

    private void configureContainer(final Composite parent, final Color background) {
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 10;
        layout.marginHeight = 6;
        layout.horizontalSpacing = 8;
        parent.setLayout(layout);
        parent.setBackground(background);
    }

    private void updateMinimumSize(final ScrolledComposite scrolled, final Composite content) {
        int availableWidth = scrolled.getClientArea().width;
        int widthHint = availableWidth > 0 ? availableWidth : SWT.DEFAULT;
        scrolled.setMinSize(content.computeSize(widthHint, SWT.DEFAULT));
    }

    private void hideViewAsync(final Display display) {
        display.asyncExec(() -> {
            if (!display.isDisposed() && getSite() != null) {
                getSite().getPage().hideView(this);
            }
        });
    }

    @Override
    public void setFocus() {
        if (parentComposite != null && !parentComposite.isDisposed()) {
            parentComposite.setFocus();
        }
    }
}
