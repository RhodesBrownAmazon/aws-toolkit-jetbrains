// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.fixtures

import com.intellij.testGuiFramework.framework.GuiTestUtil.shortcut
import com.intellij.testGuiFramework.util.Key
import com.intellij.testGuiFramework.util.Key.S
import com.intellij.testGuiFramework.util.Modifier
import com.intellij.testGuiFramework.util.Modifier.ALT
import com.intellij.testGuiFramework.util.plus

fun openSettingsDialog() {
    shortcut(Modifier.CONTROL + ALT + S, Modifier.META + Key.COMMA)
}
