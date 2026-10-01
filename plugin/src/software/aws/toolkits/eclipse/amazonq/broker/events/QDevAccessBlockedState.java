// Copyright 2024 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.eclipse.amazonq.broker.events;

/**
 * Whether Amazon Q Developer has refused this identity at sign-in.
 *
 * <p>Reacting to the refusal signs the user out, which on its own would route to the ordinary login
 * view and lose the explanation. This state is therefore resolved ahead of the logged-out state by
 * {@code ViewRouter}, so the user lands on a screen that says what happened.
 */
public enum QDevAccessBlockedState {
    NOT_BLOCKED, BLOCKED
}
