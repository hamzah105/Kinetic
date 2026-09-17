package dev.kinetic.data.persistence

import androidx.room.withTransaction
import dev.kinetic.core.agent.AgentError
import dev.kinetic.core.logging.JournalSanitizer
import dev.kinetic.core.policy.ApprovalDecision
import dev.kinetic.core.policy.ApprovalRequest
import dev.kinetic.core.policy.CapabilityMetadata
import dev.kinetic.core.policy.CapabilityCategory
import dev.kinetic.core.policy.DistributionProfile
import dev.kinetic.core.policy.RiskLevel
import dev.kinetic.core.session.DurableApprovalRecord
import dev.kinetic.core.session.DurableApprovalStatus
import dev.kinetic.core.session.DurableEffectRecord
import dev.kinetic.core.session.DurableEffectStatus
import dev.kinetic.core.session.DurableRunSnapshot
import dev.kinetic.core.session.DurableTurnRecord
import dev.kinetic.core.session.DurableTurnStatus
import dev.kinetic.core.session.LEGACY_INPUT_BINDING
import dev.kinetic.core.session.RunLedger
import dev.kinetic.core.tools.EchoInput
import dev.kinetic.core.tools.ComposeEmailInput
import dev.kinetic.core.tools.CopyTextToClipboardInput
import dev.kinetic.core.tools.NoToolInput
import dev.kinetic.core.tools.OpenHttpsUrlInput
import dev.kinetic.core.tools.OpenDialerInput
import dev.kinetic.core.tools.OpenSettingsInput
import dev.kinetic.core.tools.ProtectedDemoInput
import dev.kinetic.core.tools.SettingsDestination
import dev.kinetic.core.tools.ShareTextInput
import dev.kinetic.core.tools.ToolCall
import dev.kinetic.core.tools.ToolInput
import dev.kinetic.core.tools.ToolInputKind
import java.time.Instant
import java.util.Base64

internal class RoomRunLedger(
    private val database: KineticDatabase,
) : RunLedger {
    private val dao = database.kineticDao()

    override suspend fun beginTurn(turn: DurableTurnRecord) = database.withTransaction {
        requireNotNull(dao.session(turn.sessionId)) { "Session must exist before a turn" }
        dao.insertTurn(turn.toEntity())
    }

    override suspend fun prepareEffect(
        sessionId: String,
        turnId: String,
        callId: String,
        toolId: String,
        at: Instant,
        inputBinding: String,
    ): Boolean = database.withTransaction {
        val turn = requireNotNull(dao.turn(turnId)) { "Unknown turn $turnId" }
        require(turn.sessionId == sessionId) { "Effect session does not own turn" }
        if (dao.effect(callId) != null) return@withTransaction false
        dao.insertEffect(
            EffectEntity(
                callId = callId,
                sessionId = sessionId,
                turnId = turnId,
                toolId = toolId,
                inputBinding = inputBinding,
                status = DurableEffectStatus.PENDING.name,
                createdAtEpochMillis = at.toEpochMilli(),
                updatedAtEpochMillis = at.toEpochMilli(),
            ),
        )
        true
    }

    override suspend fun requestApproval(request: ApprovalRequest) = database.withTransaction {
        val turn = requireNotNull(dao.turn(request.turnId)) { "Unknown turn ${request.turnId}" }
        val effect = requireNotNull(dao.effect(request.toolCall.callId)) {
            "Effect must be prepared before approval"
        }
        require(turn.sessionId == request.sessionId) { "Approval session does not own turn" }
        require(
            effect.turnId == request.turnId &&
                effect.toolId == request.toolCall.toolId &&
                (effect.inputBinding == LEGACY_INPUT_BINDING ||
                    effect.inputBinding == request.toolCall.input.authorizationBinding()),
        ) {
            "Approval is not bound to the prepared effect"
        }
        dao.insertApproval(request.toEntity())
        dao.updateTurn(
            turn.copy(
                status = DurableTurnStatus.WAITING_FOR_APPROVAL.name,
                updatedAtEpochMillis = request.requestedAt.toEpochMilli(),
            ),
        )
    }

    override suspend fun resolveApproval(
        approvalId: String,
        turnId: String,
        callId: String,
        decision: ApprovalDecision,
        at: Instant,
    ): Boolean = database.withTransaction {
        val current = dao.approval(approvalId) ?: return@withTransaction false
        if (current.turnId != turnId || current.callId != callId) return@withTransaction false
        val effect = dao.effect(callId) ?: return@withTransaction false
        if (effect.inputBinding != LEGACY_INPUT_BINDING &&
            effect.inputBinding != current.storedInput().authorizationBinding()
        ) {
            return@withTransaction false
        }
        val target = when (decision) {
            ApprovalDecision.APPROVE -> DurableApprovalStatus.APPROVED
            ApprovalDecision.REJECT -> DurableApprovalStatus.REJECTED
        }
        val status = DurableApprovalStatus.valueOf(current.status)
        if (status == target) return@withTransaction true
        if (status != DurableApprovalStatus.PENDING) return@withTransaction false
        dao.updateApproval(
            current.copy(
                status = target.name,
                resolvedAtEpochMillis = at.toEpochMilli(),
            ),
        )
        true
    }

    override suspend fun markExecuting(
        turnId: String,
        callId: String,
        at: Instant,
        inputBinding: String,
    ): Boolean =
        database.withTransaction {
            val turn = dao.turn(turnId) ?: return@withTransaction false
            val effect = dao.effect(callId) ?: return@withTransaction false
            if (effect.turnId != turnId ||
                DurableEffectStatus.valueOf(effect.status) != DurableEffectStatus.PENDING ||
                effect.inputBinding != inputBinding
            ) {
                return@withTransaction false
            }
            val approval = dao.approvalForTurn(turnId)
            if (approval != null &&
                DurableApprovalStatus.valueOf(approval.status) != DurableApprovalStatus.APPROVED
            ) {
                return@withTransaction false
            }
            dao.updateEffect(
                effect.copy(
                    status = DurableEffectStatus.EXECUTING.name,
                    updatedAtEpochMillis = at.toEpochMilli(),
                ),
            )
            dao.updateTurn(
                turn.copy(
                    status = DurableTurnStatus.EXECUTING.name,
                    updatedAtEpochMillis = at.toEpochMilli(),
                ),
            )
            true
        }

    override suspend fun completeTurn(turnId: String, summary: String, at: Instant) =
        database.withTransaction {
            val turn = requireNotNull(dao.turn(turnId)) { "Unknown turn $turnId" }
            dao.updateTurn(
                turn.copy(
                    status = DurableTurnStatus.COMPLETED.name,
                    updatedAtEpochMillis = at.toEpochMilli(),
                    completionSummary = JournalSanitizer.redact(summary),
                    errorCode = null,
                    errorMessage = null,
                ),
            )
        }

    override suspend fun completeEffectAndTurn(
        turnId: String,
        callId: String,
        outputSummary: String,
        at: Instant,
    ) = database.withTransaction {
        val turn = requireNotNull(dao.turn(turnId)) { "Unknown turn $turnId" }
        val effect = requireNotNull(dao.effect(callId)) { "Unknown effect $callId" }
        require(effect.turnId == turnId &&
            DurableEffectStatus.valueOf(effect.status) == DurableEffectStatus.EXECUTING
        ) { "Only the executing bound effect can complete" }
        val safeOutput = JournalSanitizer.redact(outputSummary)
        dao.updateEffect(
            effect.copy(
                status = DurableEffectStatus.COMPLETED.name,
                updatedAtEpochMillis = at.toEpochMilli(),
                outputSummary = safeOutput,
                errorCode = null,
            ),
        )
        dao.updateTurn(
            turn.copy(
                status = DurableTurnStatus.COMPLETED.name,
                updatedAtEpochMillis = at.toEpochMilli(),
                completionSummary = safeOutput,
                errorCode = null,
                errorMessage = null,
            ),
        )
    }

    override suspend fun completeEffect(
        turnId: String,
        callId: String,
        outputSummary: String,
        at: Instant,
    ) = database.withTransaction {
        val effect = requireNotNull(dao.effect(callId)) { "Unknown effect $callId" }
        require(
            effect.turnId == turnId &&
                DurableEffectStatus.valueOf(effect.status) == DurableEffectStatus.EXECUTING,
        ) { "Only the executing bound effect can complete" }
        dao.updateEffect(
            effect.copy(
                status = DurableEffectStatus.COMPLETED.name,
                updatedAtEpochMillis = at.toEpochMilli(),
                outputSummary = JournalSanitizer.redact(outputSummary),
                errorCode = null,
            ),
        )
    }

    override suspend fun failTurn(turnId: String, error: AgentError, at: Instant) {
        finishTurn(turnId, DurableTurnStatus.FAILED, error, at)
    }

    override suspend fun cancelTurn(turnId: String, error: AgentError, at: Instant) {
        finishTurn(turnId, DurableTurnStatus.CANCELLED, error, at)
    }

    override suspend fun latestRun(sessionId: String): DurableRunSnapshot? =
        database.withTransaction {
            val turn = dao.latestTurn(sessionId) ?: return@withTransaction null
            DurableRunSnapshot(
                turn = turn.toDomain(),
                approval = dao.approvalForTurn(turn.turnId)?.toDomain(),
                effect = dao.effectForTurn(turn.turnId)?.toDomain(),
            )
        }

    private suspend fun finishTurn(
        turnId: String,
        status: DurableTurnStatus,
        error: AgentError,
        at: Instant,
    ) = database.withTransaction {
        val turn = requireNotNull(dao.turn(turnId)) { "Unknown turn $turnId" }
        dao.updateTurn(
            turn.copy(
                status = status.name,
                updatedAtEpochMillis = at.toEpochMilli(),
                errorCode = error.code,
                errorMessage = JournalSanitizer.redact(error.userMessage),
            ),
        )
        dao.approvalForTurn(turnId)
            ?.takeIf { DurableApprovalStatus.valueOf(it.status) == DurableApprovalStatus.PENDING }
            ?.let {
                dao.updateApproval(
                    it.copy(
                        status = DurableApprovalStatus.CANCELLED.name,
                        resolvedAtEpochMillis = at.toEpochMilli(),
                    ),
                )
            }
        dao.effectForTurn(turnId)
            ?.takeIf { DurableEffectStatus.valueOf(it.status) != DurableEffectStatus.COMPLETED }
            ?.let {
                dao.updateEffect(
                    it.copy(
                        status = DurableEffectStatus.FAILED.name,
                        updatedAtEpochMillis = at.toEpochMilli(),
                        errorCode = error.code,
                    ),
                )
            }
    }
}

private const val VALUE_SEPARATOR = "\u001F"

private fun DurableTurnRecord.toEntity() = TurnEntity(
    turnId = turnId,
    sessionId = sessionId,
    requestId = requestId,
    requestSummary = JournalSanitizer.redact(requestSummary),
    status = status.name,
    createdAtEpochMillis = createdAt.toEpochMilli(),
    updatedAtEpochMillis = updatedAt.toEpochMilli(),
    completionSummary = completionSummary,
    errorCode = errorCode,
    errorMessage = errorMessage,
)

private fun TurnEntity.toDomain() = DurableTurnRecord(
    turnId = turnId,
    sessionId = sessionId,
    requestId = requestId,
    requestSummary = requestSummary,
    status = DurableTurnStatus.valueOf(status),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    completionSummary = completionSummary,
    errorCode = errorCode,
    errorMessage = errorMessage,
)

private fun ApprovalRequest.toEntity(): ApprovalEntity {
    val (kind, payload) = toolCall.input.toStoredInput()
    return ApprovalEntity(
        approvalId = approvalId,
        sessionId = sessionId,
        turnId = turnId,
        callId = toolCall.callId,
        toolId = toolCall.toolId,
        inputKind = kind.name,
        inputPayload = payload,
        riskLevel = capability.riskLevel.name,
        requiresConfirmation = capability.requiresConfirmation,
        requiredPermissions = capability.requiredPermissions.sorted().joinToString(VALUE_SEPARATOR),
        distributionAvailability = capability.distributionAvailability
            .map(DistributionProfile::name)
            .sorted()
            .joinToString(VALUE_SEPARATOR),
        capabilityCategory = capability.category.name,
        crossesApplicationBoundary = capability.crossesApplicationBoundary,
        status = DurableApprovalStatus.PENDING.name,
        requestedAtEpochMillis = requestedAt.toEpochMilli(),
    )
}

private fun ApprovalEntity.toDomain() = DurableApprovalRecord(
    request = ApprovalRequest(
        approvalId = approvalId,
        sessionId = sessionId,
        turnId = turnId,
        toolCall = ToolCall(
            callId = callId,
            toolId = toolId,
            input = storedInput(),
        ),
        capability = CapabilityMetadata(
            riskLevel = RiskLevel.valueOf(riskLevel),
            requiresConfirmation = requiresConfirmation,
            requiredPermissions = requiredPermissions.toStoredSet(),
            distributionAvailability = distributionAvailability.toStoredSet()
                .mapTo(mutableSetOf(), DistributionProfile::valueOf),
            category = CapabilityCategory.valueOf(capabilityCategory),
            crossesApplicationBoundary = crossesApplicationBoundary,
        ),
        requestedAt = Instant.ofEpochMilli(requestedAtEpochMillis),
    ),
    status = DurableApprovalStatus.valueOf(status),
    resolvedAt = resolvedAtEpochMillis?.let(Instant::ofEpochMilli),
)

private fun EffectEntity.toDomain() = DurableEffectRecord(
    callId = callId,
    sessionId = sessionId,
    turnId = turnId,
    toolId = toolId,
    inputBinding = inputBinding,
    status = DurableEffectStatus.valueOf(status),
    createdAt = Instant.ofEpochMilli(createdAtEpochMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtEpochMillis),
    outputSummary = outputSummary,
    errorCode = errorCode,
)

private fun ToolInput.toStoredInput(): Pair<ToolInputKind, String?> = when (this) {
    is dev.kinetic.core.tools.McpToolInput -> ToolInputKind.MCP_JSON to canonicalJson
    is dev.kinetic.core.tools.AppFunctionToolInput -> ToolInputKind.APPFUNCTION_JSON to canonicalJson
    NoToolInput -> ToolInputKind.NONE to null
    is EchoInput -> ToolInputKind.ECHO_TEXT to text
    is ProtectedDemoInput -> ToolInputKind.PROTECTED_ACTION to action
    is OpenHttpsUrlInput -> ToolInputKind.HTTPS_URL to url
    is ShareTextInput -> ToolInputKind.SHARE_TEXT to text
    is OpenSettingsInput -> ToolInputKind.SETTINGS_DESTINATION to destination.name
    is CopyTextToClipboardInput -> ToolInputKind.CLIPBOARD_TEXT to text
    is OpenDialerInput -> ToolInputKind.PHONE_NUMBER to phoneNumber
    is ComposeEmailInput -> ToolInputKind.EMAIL_COMPOSITION to encodeEmailInput()
}

private fun ApprovalEntity.storedInput(): ToolInput = when (ToolInputKind.valueOf(inputKind)) {
    ToolInputKind.MCP_JSON -> dev.kinetic.core.tools.McpToolInput(inputPayload.orEmpty())
    ToolInputKind.APPFUNCTION_JSON -> dev.kinetic.core.tools.AppFunctionToolInput(inputPayload.orEmpty())
    ToolInputKind.NONE -> NoToolInput
    ToolInputKind.ECHO_TEXT -> EchoInput(inputPayload.orEmpty())
    ToolInputKind.PROTECTED_ACTION -> ProtectedDemoInput(inputPayload.orEmpty())
    ToolInputKind.HTTPS_URL -> OpenHttpsUrlInput(inputPayload.orEmpty())
    ToolInputKind.SHARE_TEXT -> ShareTextInput(inputPayload.orEmpty())
    ToolInputKind.SETTINGS_DESTINATION -> OpenSettingsInput(
        SettingsDestination.valueOf(inputPayload.orEmpty()),
    )
    ToolInputKind.CLIPBOARD_TEXT -> CopyTextToClipboardInput(inputPayload.orEmpty())
    ToolInputKind.PHONE_NUMBER -> OpenDialerInput(inputPayload.orEmpty())
    ToolInputKind.EMAIL_COMPOSITION -> decodeEmailInput(inputPayload.orEmpty())
}

private fun ComposeEmailInput.encodeEmailInput(): String =
    listOf(recipient, subject, body).joinToString(EMAIL_FIELD_SEPARATOR) { value ->
        value?.let { Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8)) }.orEmpty()
    }

private fun decodeEmailInput(payload: String): ComposeEmailInput {
    val fields = payload.split(EMAIL_FIELD_SEPARATOR, limit = 3)
    require(fields.size == 3) { "Stored email composition payload is invalid" }
    fun decode(value: String): String? = value.takeIf(String::isNotEmpty)?.let {
        Base64.getDecoder().decode(it).toString(Charsets.UTF_8)
    }
    return ComposeEmailInput(decode(fields[0]), decode(fields[1]), decode(fields[2]))
}

private fun String.toStoredSet(): Set<String> =
    takeIf(String::isNotEmpty)?.split(VALUE_SEPARATOR)?.toSet().orEmpty()

private const val EMAIL_FIELD_SEPARATOR = "."
