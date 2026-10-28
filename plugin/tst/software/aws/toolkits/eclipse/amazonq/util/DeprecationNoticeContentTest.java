// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public final class DeprecationNoticeContentTest {

    @Test
    void usesApprovedEditorBannerContentAndLinks() {
        assertEquals("Amazon Q Developer IDE plugins: end of support",
                Constants.DEPRECATION_NOTICE_TITLE);
        assertEquals("On April 30, 2027, AWS will discontinue support for Amazon Q Developer IDE plugins. "
                + "For capabilities similar to Amazon Q Developer IDE plugins, explore Kiro to access the latest models and features, "
                + "including agentic coding, chat and MCP support.",
                Constants.DEPRECATION_NOTICE_BODY);
        assertEquals("https://kiro.dev", Constants.KIRO_URL);
        assertEquals("https://aws.amazon.com/blogs/devops/amazon-q-developer-end-of-support-announcement/",
                Constants.DEPRECATION_NOTICE_LEARN_MORE_URL);
    }
}
