package com.danzucker.stitchpad.feature.smart.data

import com.danzucker.stitchpad.core.domain.error.Result
import com.danzucker.stitchpad.core.domain.repository.CustomerRepository
import com.danzucker.stitchpad.feature.auth.domain.AuthRepository
import com.danzucker.stitchpad.feature.smart.domain.model.CustomerSummary
import com.danzucker.stitchpad.feature.smart.presentation.draft.CustomerSearchProvider
import kotlinx.coroutines.flow.first

internal class SmartCustomerSearchAdapter(
    private val authRepository: AuthRepository,
    private val customerRepository: CustomerRepository,
) : CustomerSearchProvider {

    override suspend fun search(query: String): List<CustomerSummary> {
        val userId = authRepository.getCurrentUser()?.id ?: return emptyList()
        return when (val result = customerRepository.observeCustomers(userId).first()) {
            is Result.Success ->
                result.data
                    .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
                    .map { customer ->
                        CustomerSummary(
                            id = customer.id,
                            firstName = customer.name.substringBefore(' ').ifBlank { customer.name },
                            whatsappNumber = customer.phone.takeIf { it.isNotBlank() },
                        )
                    }
            is Result.Error -> emptyList()
        }
    }
}
