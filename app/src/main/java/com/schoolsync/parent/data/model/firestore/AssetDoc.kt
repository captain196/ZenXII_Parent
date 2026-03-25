package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class AssetDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val name: String = "",
    val category: String = "",
    val subCategory: String = "",
    val assetTag: String = "",
    val serialNo: String = "",
    val purchaseDate: String = "",
    val purchasePrice: Double = 0.0,
    val vendor: String = "",
    val warrantyUntil: String = "",
    val location: String = "",
    val custodian: String = "",
    val custodianName: String = "",
    val condition: String = "good",
    val currentValue: Double = 0.0,
    val depreciationRate: Double = 0.0,
    val lastMaintenanceDate: String = "",
    val nextMaintenanceDate: String = "",
    val status: String = "in_use"
)
