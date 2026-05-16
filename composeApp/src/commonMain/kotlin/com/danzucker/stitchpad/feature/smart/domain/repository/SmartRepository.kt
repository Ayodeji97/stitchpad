package com.danzucker.stitchpad.feature.smart.domain.repository

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.feature.smart.domain.error.SmartError
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageRequest
import com.danzucker.stitchpad.feature.smart.domain.model.DraftMessageResult

/**
 * Server-backed Smart Suggestions repository. Implementations call the
 * smartDraftMessage Cloud Function via Firebase Functions client.
 */
interface SmartRepository {
    suspend fun draftMessage(
        request: DraftMessageRequest,
    ): Result<DraftMessageResult, SmartError>
}
