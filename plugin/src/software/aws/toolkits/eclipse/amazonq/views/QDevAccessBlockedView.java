// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.views;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Link;

import software.aws.toolkits.eclipse.amazonq.broker.events.QDevAccessBlockedState;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.util.PluginUtils;

/**
 * Shown when Amazon Q Developer refuses this identity at sign-in.
 *
 * <p>Amazon Q Developer stopped accepting new Builder ID accounts. Such an account signs in
 * successfully -- sign-in is OIDC and is never gated -- and then finds Q silently non-functional,
 * with the service's refusal arriving as if it were a chat reply. This view replaces that dead end
 * with an explanation, a pointer to Kiro, and a route back to sign-in for anyone whose Builder ID
 * predates the cutoff.
 *
 * <p>The dates and URLs below are product copy taken from the public announcement, not values
 * reported by the service. The service's own message is deliberately not displayed: it is a single
 * sentence written for an API consumer, and it does not say what the user should do next.
 */
public final class QDevAccessBlockedView extends CallToActionView {

    public static final String ID = "software.aws.toolkits.eclipse.amazonq.views.QDevAccessBlockedView";

    private static final String ICON_PATH = "icons/AmazonQ64.png";
    private static final String HEADER_LABEL = "New sign-ups are no longer available";
    private static final String SIGNUP_CUTOFF_DATE = "May 15, 2026";
    private static final String END_OF_SUPPORT_DATE = "April 30, 2027";
    private static final String DETAIL_MESSAGE = "Amazon Q Developer stopped accepting new accounts as of "
            + SIGNUP_CUTOFF_DATE + ". Amazon Q Developer IDE plugins are reaching end of support on "
            + END_OF_SUPPORT_DATE + "."
            + System.lineSeparator() + System.lineSeparator()
            + "Kiro includes all the AI coding features from Q Developer, plus spec-driven development and more."
            + System.lineSeparator() + System.lineSeparator()
            + "If your Builder ID was created before " + SIGNUP_CUTOFF_DATE
            + ", you can still sign in -- only newly created accounts are blocked.";
    private static final String BUTTON_LABEL = "Get started with Kiro";
    private static final String LINK_LABEL = "Try a different login method";

    private static final String KIRO_URL = "https://kiro.dev";

    @Override
    protected String getIconPath() {
        return ICON_PATH;
    }

    @Override
    protected String getHeaderLabel() {
        return HEADER_LABEL;
    }

    @Override
    protected String getDetailMessage() {
        return DETAIL_MESSAGE;
    }

    @Override
    protected String getButtonLabel() {
        return BUTTON_LABEL;
    }

    @Override
    protected SelectionListener getButtonHandler() {
        return new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                PluginUtils.openWebpage(KIRO_URL);
            }
        };
    }

    @Override
    protected void setupButtonFooterContent(final Composite composite) {
        Link hyperlink = new Link(composite, SWT.NONE);
        hyperlink.setText("<a>" + LINK_LABEL + "</a>");
        hyperlink.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, false));
        hyperlink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent e) {
                /*
                 * Clearing the state is what returns the user to sign-in: reacting to the refusal
                 * already signed them out, so the router resolves the logged-out state as soon as
                 * this view stops taking priority. Signing out again here would be a no-op.
                 */
                Activator.getEventBroker().post(QDevAccessBlockedState.class, QDevAccessBlockedState.NOT_BLOCKED);
            }
        });
    }
}
