// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.extensions.implementation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.MockedStatic;

import software.aws.toolkits.eclipse.amazonq.extensions.api.StaticMockExtension;
import software.aws.toolkits.eclipse.amazonq.util.ThreadingUtils;

public final class ThreadingUtilsStaticMockExtension extends StaticMockExtension<ThreadingUtils>
        implements BeforeAllCallback, AfterAllCallback {

    private MockedStatic<ThreadingUtils> threadingUtilsStaticMock = null;

    @Override
    public MockedStatic<ThreadingUtils> getStaticMock() {
        return threadingUtilsStaticMock;
    }

    @Override
    public void beforeAll(final ExtensionContext context) {
        threadingUtilsStaticMock = mockStatic(ThreadingUtils.class);

        threadingUtilsStaticMock.when(() -> ThreadingUtils.executeAsyncTask(any(Runnable.class))).then(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        });
        threadingUtilsStaticMock.when(() -> ThreadingUtils.executeAsyncTaskAndReturnFuture(any(Runnable.class)))
                .then(invocation -> {
                    Runnable task = invocation.getArgument(0);
                    task.run();
                    return null;
                });
    }

    @Override
    public void afterAll(final ExtensionContext context) throws Exception {
        if (threadingUtilsStaticMock != null) {
            threadingUtilsStaticMock.close();
        }
    }

}
