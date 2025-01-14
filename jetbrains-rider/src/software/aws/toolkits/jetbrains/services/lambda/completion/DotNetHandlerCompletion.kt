// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.completion

import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.jetbrains.rd.framework.impl.RpcTimeouts
import com.jetbrains.rdclient.icons.FrontendIconHost
import com.jetbrains.rider.model.lambdaModel
import com.jetbrains.rider.projectView.solution

class DotNetHandlerCompletion : HandlerCompletion {

    override fun getPrefixMatcher(prefix: String): PrefixMatcher =
        CamelHumpMatcher(prefix)

    override fun getLookupElements(project: Project): Collection<LookupElement> {
        val completionItems = project.solution.lambdaModel.determineHandlers.sync(Unit, RpcTimeouts.default)
        return completionItems.map { completionItem ->
            LookupElementBuilder.create(completionItem.handler).let {
                if (completionItem.iconId != null) it.withIcon(FrontendIconHost.getInstance(project).toIdeaIcon(completionItem.iconId))
                else it
            }
        }
    }
}
