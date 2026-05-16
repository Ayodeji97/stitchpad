package com.danzucker.stitchpad.feature.smart.presentation.draft

import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary

interface CustomerSearchProvider {
    suspend fun search(query: String): List<CustomerSummary>
}
