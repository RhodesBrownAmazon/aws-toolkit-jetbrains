// Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
// SPDX-License-Identifier: Apache-2.0

package software.aws.toolkits.jetbrains.services.lambda.nodejs

import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.xdebugger.XDebuggerUtil
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.ide.BuiltInServerManager
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.services.lambda.model.Runtime
import software.aws.toolkits.jetbrains.core.credentials.MockCredentialsManager
import software.aws.toolkits.jetbrains.services.lambda.execution.local.createHandlerBasedRunConfiguration
import software.aws.toolkits.jetbrains.services.lambda.execution.local.createTemplateRunConfiguration
import software.aws.toolkits.jetbrains.services.lambda.sam.SamOptions
import software.aws.toolkits.jetbrains.services.lambda.sam.checkBreakPointHit
import software.aws.toolkits.jetbrains.services.lambda.sam.executeLambda
import software.aws.toolkits.jetbrains.settings.SamSettings
import softwere.aws.toolkits.jetbrains.utils.rules.NodeJsCodeInsightTestFixtureRule
import softwere.aws.toolkits.jetbrains.utils.rules.addPackageJsonFile
import java.util.concurrent.atomic.AtomicReference

@RunWith(Parameterized::class)
class NodeJsLocalLambdaRunConfigurationIntegrationTest(private val runtime: Runtime) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters(): Collection<Array<Runtime>> = listOf(
            arrayOf(Runtime.NODEJS8_10),
            arrayOf(Runtime.NODEJS10_X)
        )
    }

    @Rule
    @JvmField
    val projectRule = NodeJsCodeInsightTestFixtureRule()

    private val mockId = "MockCredsId"
    private val mockCreds = AwsBasicCredentials.create("Access", "ItsASecret")
    private val serverStarted = AtomicReference<Boolean>(false)

    @Before
    fun setUp() {
        SamSettings.getInstance().savedExecutablePath = System.getenv()["SAM_CLI_EXEC"]

        val fixture = projectRule.fixture

        val psiFile = fixture.addFileToProject(
            "hello_world/app.js",
            """
            exports.lambdaHandler = async (event, context) => {
                return 'Hello World'
            };
            """.trimIndent()
        )

        runInEdtAndWait {
            fixture.openFileInEditor(psiFile.virtualFile)
        }

        MockCredentialsManager.getInstance().addCredentials(mockId, mockCreds)
        ensureServerStarted()
    }

    @After
    fun tearDown() {
        MockCredentialsManager.getInstance().reset()
    }

    @Test
    fun samIsExecuted() {
        projectRule.fixture.addPackageJsonFile()

        val runConfiguration = createHandlerBasedRunConfiguration(
            project = projectRule.project,
            runtime = runtime,
            handler = "hello_world/app.lambdaHandler",
            input = "\"Hello World\"",
            credentialsProviderId = mockId
        )

        assertThat(runConfiguration).isNotNull

        val executeLambda = executeLambda(runConfiguration)

        assertThat(executeLambda.exitCode).isEqualTo(0)
        assertThat(executeLambda.stdout).contains("Hello World")
    }

    @Test
    fun samIsExecutedWithContainer() {
        projectRule.fixture.addPackageJsonFile()

        val samOptions = SamOptions().apply {
            this.buildInContainer = true
        }

        val runConfiguration = createHandlerBasedRunConfiguration(
            project = projectRule.project,
            runtime = runtime,
            handler = "hello_world/app.lambdaHandler",
            input = "\"Hello World\"",
            credentialsProviderId = mockId,
            samOptions = samOptions
        )

        assertThat(runConfiguration).isNotNull

        val executeLambda = executeLambda(runConfiguration)

        assertThat(executeLambda.exitCode).isEqualTo(0)
        assertThat(executeLambda.stdout).contains("Hello World")
    }

    @Test
    fun samIsExecutedWhenRunWithATemplateServerless() {
        projectRule.fixture.addPackageJsonFile(subPath = "hello_world")

        val templateFile = projectRule.fixture.addFileToProject(
            "template.yaml", """
            Resources:
              SomeFunction:
                Type: AWS::Serverless::Function
                Properties:
                  Handler: app.lambdaHandler
                  CodeUri: hello_world
                  Runtime: $runtime
                  Timeout: 900
        """.trimIndent()
        )

        val runConfiguration = createTemplateRunConfiguration(
            project = projectRule.project,
            templateFile = templateFile.containingFile.virtualFile.path,
            logicalId = "SomeFunction",
            input = "\"Hello World\"",
            credentialsProviderId = mockId
        )

        assertThat(runConfiguration).isNotNull

        val executeLambda = executeLambda(runConfiguration)

        assertThat(executeLambda.exitCode).isEqualTo(0)
        assertThat(executeLambda.stdout).contains("Hello World")
    }

    @Test
    fun samIsExecutedWithDebugger() {
        projectRule.fixture.addPackageJsonFile()

        val runConfiguration = createHandlerBasedRunConfiguration(
            project = projectRule.project,
            runtime = runtime,
            handler = "hello_world/app.lambdaHandler",
            input = "\"Hello World\"",
            credentialsProviderId = mockId
        )

        assertThat(runConfiguration).isNotNull

        addBreakpoint(2)

        val debuggerIsHit = checkBreakPointHit(projectRule.project)
        val executeLambda = executeLambda(runConfiguration, DefaultDebugExecutor.EXECUTOR_ID)

        assertThat(executeLambda.exitCode).isEqualTo(137)
        assertThat(executeLambda.stdout).contains("Hello World")

        assertThat(debuggerIsHit.get()).isTrue()
    }

    @Test
    fun samIsExecutedWithDebugger_sameFileNames() {
        projectRule.fixture.addPackageJsonFile()

        val psiFile = projectRule.fixture.addFileToProject(
            "hello_world/subfolder/app.js",
            """
            exports.lambdaHandler = async (event, context) => {
                return 'Hello World'
            };
            """.trimIndent()
        )

        runInEdtAndWait {
            projectRule.fixture.openFileInEditor(psiFile.virtualFile)
        }

        val runConfiguration = createHandlerBasedRunConfiguration(
            project = projectRule.project,
            runtime = runtime,
            handler = "hello_world/subfolder/app.lambdaHandler",
            input = "\"Hello World\"",
            credentialsProviderId = mockId
        )

        assertThat(runConfiguration).isNotNull

        addBreakpoint(2)

        val debuggerIsHit = checkBreakPointHit(projectRule.project)
        val executeLambda = executeLambda(runConfiguration, DefaultDebugExecutor.EXECUTOR_ID)

        assertThat(executeLambda.exitCode).isEqualTo(137)
        assertThat(executeLambda.stdout).contains("Hello World")

        assertThat(debuggerIsHit.get()).isTrue()
    }

    private fun ensureServerStarted() {
        serverStarted.getAndUpdate { started ->
            if (!started) {
                BuiltInServerManager.getInstance().waitForStart()
            }
            true
        }
    }

    private fun addBreakpoint(lineNumber: Int) {
        runInEdtAndWait {
            XDebuggerUtil.getInstance().toggleLineBreakpoint(
                projectRule.project,
                projectRule.fixture.file.virtualFile,
                lineNumber
            )
        }
    }
}
