package com.danzucker.stitchpad.feature.settings.domain

import com.danzucker.stitchpad.core.domain.error.DataError
import com.danzucker.stitchpad.core.domain.error.EmptyResult

interface DeletionFeedbackRepository {
    suspend fun submitFeedback(feedback: DeletionFeedback): EmptyResult<DataError.Network>
}
