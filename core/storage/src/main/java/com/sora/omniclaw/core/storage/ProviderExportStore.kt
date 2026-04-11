package com.sora.omniclaw.core.storage

import com.sora.omniclaw.core.common.HostResult
import com.sora.omniclaw.core.model.ProviderRuntimeExport
import kotlinx.coroutines.flow.Flow

interface ProviderExportStore {
    suspend fun readExport(): ProviderRuntimeExport?

    fun observeExport(): Flow<ProviderRuntimeExport?>

    fun observeExportReadiness(): Flow<Boolean>

    suspend fun writeExport(export: ProviderRuntimeExport): HostResult<Unit>

    suspend fun clearExport(): HostResult<Unit>
}
