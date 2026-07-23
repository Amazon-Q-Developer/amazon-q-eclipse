// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.FileFieldEditor;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferenceDialog;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.dialogs.PreferencesUtil;

import software.aws.toolkits.eclipse.amazonq.configuration.customization.CustomizationUtil;
import software.aws.toolkits.eclipse.amazonq.lsp.auth.model.LoginType;
import software.aws.toolkits.eclipse.amazonq.lsp.model.GetConfigurationFromServerParams;
import software.aws.toolkits.eclipse.amazonq.lsp.model.GetConfigurationFromServerParams.ExpectedResponseType;
import software.aws.toolkits.eclipse.amazonq.plugin.Activator;
import software.aws.toolkits.eclipse.amazonq.telemetry.AwsTelemetryProvider;
import software.aws.toolkits.eclipse.amazonq.telemetry.UiTelemetryProvider;
import software.aws.toolkits.eclipse.amazonq.util.PluginUtils;
import software.aws.toolkits.eclipse.amazonq.util.ThreadingUtils;

public class AmazonQPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
    public static final String PREFERENCE_STORE_ID = "software.aws.toolkits.eclipse.preferences";
    public static final String CODE_REFERENCE_OPT_IN = "codeReferenceOptIn";
    public static final String TELEMETRY_OPT_IN = "telemetryOptIn";
    public static final String Q_DATA_SHARING = "qDataSharing";
    public static final String HTTPS_PROXY = "httpsProxy";
    public static final String CA_CERT = "customCaCert";
    public static final String NOTIFICATIONS_OPT_IN = "notificationsOptIn";
    public static final String NOTIFICATIONS_ENDPOINT_OVERRIDE = "notificationsEndpointOverride";

    private Boolean isTelemetryOptInChecked;
    private Boolean isQDataSharingOptInChecked;

    private Boolean changedTelemetryOptInChecked;
    private Boolean changedDataSharingOptInChecked;

    private final IPreferenceStore preferenceStore;

    public AmazonQPreferencePage() {
        super(GRID);
        preferenceStore = Activator.getDefault().getPreferenceStore();
    }

    @Override
    public final void init(final IWorkbench workbench) {
        isTelemetryOptInChecked = preferenceStore.getBoolean(TELEMETRY_OPT_IN);
        changedTelemetryOptInChecked = preferenceStore.getBoolean(TELEMETRY_OPT_IN);
        isQDataSharingOptInChecked = preferenceStore.getBoolean(Q_DATA_SHARING);
        changedDataSharingOptInChecked = preferenceStore.getBoolean(Q_DATA_SHARING);
        setPreferenceStore(preferenceStore);
    }

    @Override
    protected final void createFieldEditors() {
        ((GridLayout) getFieldEditorParent().getLayout()).numColumns = 1;

        createHorizontalSeparator();
        createHeading("Code Suggestions");
        createCodeReferenceOptInField();
        createHeading("Data Sharing");
        createTelemetryOptInField();
        createHorizontalSeparator();
        createQDataSharingField();
        createHeading("Notifications");
        createNotificationsOptInField();
        createHeading("Proxy Settings");
        createHttpsProxyField();
        createCaCertField();

        GetConfigurationFromServerParams params = new GetConfigurationFromServerParams(ExpectedResponseType.DEFAULT);
        Activator.getLspProvider().getAmazonQServer().thenCompose(server -> server.getConfigurationFromServer(params));
    }

    private void createHorizontalSeparator() {
        new Label(getFieldEditorParent(), SWT.HORIZONTAL);
    }

    private void createHeading(final String text) {
        Label dataSharing = new Label(getFieldEditorParent(), SWT.NONE);
        dataSharing.setFont(JFaceResources.getFontRegistry().getBold(JFaceResources.HEADER_FONT));
        dataSharing.setText(text);
        dataSharing.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        new Label(getFieldEditorParent(), SWT.HORIZONTAL | SWT.SEPARATOR)
                .setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
    }

    private void createCodeReferenceOptInField() {
        Composite codeReferenceOptInComposite = new Composite(getFieldEditorParent(), SWT.NONE);
        codeReferenceOptInComposite.setLayout(new GridLayout(2, false));
        GridData telemetryOptInCompositeData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        telemetryOptInCompositeData.horizontalIndent = 20;
        codeReferenceOptInComposite.setLayoutData(telemetryOptInCompositeData);

        BooleanFieldEditor codeReferenceOptIn = new BooleanFieldEditor(CODE_REFERENCE_OPT_IN,
                "Show Code Suggestions with Code References", codeReferenceOptInComposite);
        addField(codeReferenceOptIn);

        if (Activator.getLoginService().getAuthState().loginType().equals(LoginType.IAM_IDENTITY_CENTER)) {
            codeReferenceOptIn.setEnabled(false, codeReferenceOptInComposite);
        }

        Link codeReferenceLink = createLink(
                """
                        Amazon Q creates a code reference when you insert a code suggestion from Amazon Q that is similar to training data.\
                        \nWhen unchecked, Amazon Q will not show code suggestions that have code references. If you authenticate through IAM\
                        \nIdentity Center, this setting is controlled by your Amazon Q administrator. \
                        \nLearn more <a href=\"https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/code-reference.html\">here</a>.
                        """,
                20, codeReferenceOptInComposite);
        codeReferenceLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent event) {
                UiTelemetryProvider.emitClickEventMetric("preferences_codeReferences");
                PluginUtils.openWebpage(event.text);
            }
        });
    }

    private void createTelemetryOptInField() {
        Composite telemetryOptInComposite = new Composite(getFieldEditorParent(), SWT.NONE);
        telemetryOptInComposite.setLayout(new GridLayout(2, false));
        GridData telemetryOptInCompositeData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        telemetryOptInCompositeData.horizontalIndent = 20;
        telemetryOptInComposite.setLayoutData(telemetryOptInCompositeData);

        BooleanFieldEditor telemetryOptIn = new BooleanFieldEditor(TELEMETRY_OPT_IN, "Send usage metrics to AWS",
                telemetryOptInComposite) {
            @Override
            protected void valueChanged(final boolean oldValue, final boolean newValue) {
                changedTelemetryOptInChecked = newValue;
            }
        };
        addField(telemetryOptIn);

        Link telemetryLink = createLink("See more details <a href=\"https://docs.aws.amazon.com/amazonq/latest/qdeveloper-ug/opt-out-IDE.html"
                + "#opt-out-IDE-telemetry\">here</a>.", 20, telemetryOptInComposite);
        telemetryLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent event) {
                UiTelemetryProvider.emitClickEventMetric("preferences_telemetryLink");
                PluginUtils.openWebpage(event.text);
            }
        });
    }

    private void createNotificationsOptInField() {
        Composite notificationsOptInComposite = new Composite(getFieldEditorParent(), SWT.NONE);
        notificationsOptInComposite.setLayout(new GridLayout(2, false));
        GridData notificationsOptInCompositeData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        notificationsOptInCompositeData.horizontalIndent = 20;
        notificationsOptInComposite.setLayoutData(notificationsOptInCompositeData);

        BooleanFieldEditor notificationsOptIn = new BooleanFieldEditor(NOTIFICATIONS_OPT_IN,
                "Show Amazon Q notifications about known issues and available fixes", notificationsOptInComposite);
        addField(notificationsOptIn);
    }

    private void createQDataSharingField() {
        Composite qDataSharingComposite = new Composite(getFieldEditorParent(), SWT.NONE);
        qDataSharingComposite.setLayout(new GridLayout(2, false));
        GridData qDataSharingCompositeData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        qDataSharingCompositeData.horizontalIndent = 20;
        qDataSharingComposite.setLayoutData(qDataSharingCompositeData);

        BooleanFieldEditor qDataSharing = new BooleanFieldEditor(Q_DATA_SHARING, "Share Amazon Q Content with AWS",
                qDataSharingComposite) {
            @Override
            protected void valueChanged(final boolean oldValue, final boolean newValue) {
                changedDataSharingOptInChecked = newValue;
            }
        };
        addField(qDataSharing);

        Link dataSharingLink = createLink(
                """
                        When checked, your content processed by Amazon Q may be used for service improvement (except for content processed\
                        \nby the Amazon Q Developer Pro tier). Unchecking this box will cause AWS to delete any of your content used for that\
                        \npurpose. The information used to provide the Amazon Q service to you will not be affected.\
                        \nSee the <a href="https://aws.amazon.com/service-terms/">Service Terms</a> for more details.
                        """,
                20, qDataSharingComposite);
        dataSharingLink.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent event) {
                UiTelemetryProvider.emitClickEventMetric("preferences_dataSharingLink");
                PluginUtils.openWebpage(event.text);
            }
        });
    }

    private void createHttpsProxyField() {
        Composite httpsProxyComposite = new Composite(getFieldEditorParent(), SWT.NONE);
        httpsProxyComposite.setLayout(new GridLayout(2, false));
        GridData httpsProxyCompositeData = new GridData(SWT.LEFT, SWT.CENTER, true, false);
        httpsProxyCompositeData.horizontalIndent = 20;
        httpsProxyComposite.setLayoutData(httpsProxyCompositeData);
        StringFieldEditor httpsProxy = new StringFieldEditor(HTTPS_PROXY, "HTTPS Proxy URL", 65, httpsProxyComposite);
        httpsProxy.setEmptyStringAllowed(true);
        addField(httpsProxy);
        createLabel("""
                Sets the address of the proxy to use for all HTTPS connections. \
                For example "http://localhost:8888". \
                Leave blank if not using a proxy.
                Eclipse restart required to take effect.
                """, 20, getFieldEditorParent());
    }

    private void createCaCertField() {
        Composite caCertComposite = new Composite(getFieldEditorParent(), SWT.NONE);
        caCertComposite.setLayout(new GridLayout(2, false));
        GridData caCertCompositeData = new GridData(SWT.LEFT, SWT.CENTER, true, false);
        caCertCompositeData.horizontalIndent = 20;
        caCertCompositeData.widthHint = 715;
        caCertComposite.setLayoutData(caCertCompositeData);

        FileFieldEditor caCert = new FileFieldEditor(CA_CERT, "CA Cert PEM", true, StringFieldEditor.VALIDATE_ON_KEY_STROKE, caCertComposite);
        caCert.setFileExtensions(new String[] {"*.pem"});
        caCert.setErrorMessage("CA cert must be an existing PEM file");
        addField(caCert);
        createLabel("""
                Absolute path to file containing extra certificates to extend beyond the root CAs. \
                Leave blank for default CA certs.
                Eclipse restart required to take effect.
                """, 20, getFieldEditorParent());
    }

    private Link createLink(final String text, final int horizontalIndent, final Composite parent) {
        Link link = new Link(parent, SWT.NONE);
        link.setText(text);
        GridData linkData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        linkData.horizontalIndent = horizontalIndent;
        link.setLayoutData(linkData);
        return link;
    }

    private Label createLabel(final String text, final int horizontalIndent, final Composite parent) {
        Label label = new Label(parent, SWT.HORIZONTAL);
        label.setText(text);
        GridData labelData = new GridData(SWT.FILL, SWT.CENTER, true, false);
        labelData.horizontalIndent = horizontalIndent;
        label.setLayoutData(labelData);
        return label;
    }

    @Override
    protected final void performDefaults() {
        sendUpdatedPreferences();
        super.performDefaults();
    }

    @Override
    protected final void performApply() {
        sendUpdatedPreferences();
        super.performApply();
    }

    @Override
    public final boolean performOk() {
        sendUpdatedPreferences();
        return super.performOk();
    }

    private void sendUpdatedPreferences() {
        if (changedTelemetryOptInChecked != isTelemetryOptInChecked) {
            AwsTelemetryProvider.emitModifySettingEvent("amazonQ.telemetry", changedTelemetryOptInChecked.toString());
            isTelemetryOptInChecked = changedTelemetryOptInChecked;
        }

        if (changedDataSharingOptInChecked != isQDataSharingOptInChecked) {
            AwsTelemetryProvider.emitModifySettingEvent("amazonQ.dataSharing",
                    changedDataSharingOptInChecked.toString());
            isQDataSharingOptInChecked = changedDataSharingOptInChecked;
        }

        ThreadingUtils.executeAsyncTask(() -> CustomizationUtil.triggerChangeConfigurationNotification());
    }

    @Override
    protected void adjustGridLayout() {
        // deliberately left blank to prevent multiple columns from implicitly being created
    }

    public static void openPreferencePane() {
        Display.getDefault().asyncExec(() -> {
            PreferenceDialog dialog = PreferencesUtil.createPreferenceDialogOn(
                    Display.getDefault().getActiveShell(),
                    "software.aws.toolkits.eclipse.amazonq.preferences.AmazonQPreferencePage",
                    new String[] {"software.aws.toolkits.eclipse.amazonq.preferences.AmazonQPreferencePage"},
                    null
                );
            dialog.open();
        });
    }

}
