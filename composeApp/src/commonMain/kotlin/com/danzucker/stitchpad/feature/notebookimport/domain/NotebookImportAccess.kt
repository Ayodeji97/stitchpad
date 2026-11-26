package com.danzucker.stitchpad.feature.notebookimport.domain

import com.danzucker.stitchpad.core.domain.entitlement.UserEntitlements
import com.danzucker.stitchpad.core.domain.session.WorkshopSession

@Suppress("UNUSED_PARAMETER")
fun canStartNotebookImport(session: WorkshopSession, entitlements: UserEntitlements): Boolean = session.isOwner
