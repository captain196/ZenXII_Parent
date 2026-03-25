package com.schoolsync.parent.data.model

/**
 * Fee structure for a class/section.
 * Path: Schools/{schoolCode}/{session}/Accounts/Fees/Classes Fees/{class}/{section}/
 */
data class FeeStructure(
    val className: String = "",
    val section: String = "",
    val feeHeads: List<FeeHead> = emptyList(),
    val totalAnnualFee: Double = 0.0,
    val rawData: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun fromMap(className: String, section: String, data: Map<String, Any?>): FeeStructure {
            val feeHeads = mutableListOf<FeeHead>()
            var total = 0.0

            data.forEach { (key, value) ->
                val displayName = FeeHead.formatName(key)
                when (value) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val headData = value as Map<String, Any?>
                        val amount = headData["amount"].toSafeDouble()
                        val frequency = (headData["frequency"] ?: "Monthly").toString()
                        val description = (headData["description"] ?: "").toString()
                        feeHeads.add(
                            FeeHead(
                                name = displayName,
                                amount = amount,
                                frequency = frequency,
                                description = description
                            )
                        )
                        total += amount
                    }
                    is Number -> {
                        feeHeads.add(
                            FeeHead(
                                name = displayName,
                                amount = value.toDouble(),
                                frequency = "Monthly",
                                description = ""
                            )
                        )
                        total += value.toDouble()
                    }
                    is String -> {
                        val amount = value.toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            feeHeads.add(
                                FeeHead(
                                    name = displayName,
                                    amount = amount,
                                    frequency = "Monthly",
                                    description = ""
                                )
                            )
                            total += amount
                        }
                    }
                }
            }

            return FeeStructure(
                className = className,
                section = section,
                feeHeads = feeHeads,
                totalAnnualFee = total,
                rawData = data
            )
        }

        private fun Any?.toSafeDouble(): Double {
            return when (this) {
                is Number -> this.toDouble()
                is String -> this.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        }
    }
}

data class FeeHead(
    val name: String,
    val amount: Double,
    val frequency: String = "Monthly",  // Monthly, Quarterly, Annual, One-time
    val description: String = ""
) {
    companion object {
        /**
         * Convert Firebase keys like "Tuition_Fee" or "lab_charges" into
         * human-readable names: "Tuition Fee", "Lab Charges".
         */
        fun formatName(rawKey: String): String =
            rawKey.replace('_', ' ')
                .trim()
                .split("\\s+".toRegex())
                .joinToString(" ") { word ->
                    word.replaceFirstChar { it.uppercase() }
                }
    }
}

/**
 * Pending fees for a student.
 * Path: Schools/{schoolCode}/{session}/Accounts/Pending_fees/{studentId}
 */
data class PendingFees(
    val studentId: String = "",
    val totalPending: Double = 0.0,
    val pendingMonths: List<PendingMonth> = emptyList(),
    val rawData: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun fromMap(studentId: String, data: Map<String, Any?>): PendingFees {
            val pendingMonths = mutableListOf<PendingMonth>()
            var total = 0.0

            data.forEach { (key, value) ->
                val displayMonth = PendingMonth.formatMonthKey(key)
                when (value) {
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        val monthData = value as Map<String, Any?>
                        val amount = monthData["amount"].toSafeDouble()
                        val dueDate = (monthData["due_date"] ?: "").toString()
                        val status = (monthData["status"] ?: "Pending").toString()
                        pendingMonths.add(
                            PendingMonth(
                                month = displayMonth,
                                amount = amount,
                                dueDate = dueDate,
                                status = status
                            )
                        )
                        if (status.equals("Pending", ignoreCase = true) ||
                            status.equals("Overdue", ignoreCase = true)
                        ) {
                            total += amount
                        }
                    }
                    is Number -> {
                        pendingMonths.add(
                            PendingMonth(
                                month = displayMonth,
                                amount = value.toDouble(),
                                dueDate = "",
                                status = "Pending"
                            )
                        )
                        total += value.toDouble()
                    }
                    is String -> {
                        val amount = value.toDoubleOrNull() ?: 0.0
                        if (amount > 0) {
                            pendingMonths.add(
                                PendingMonth(month = displayMonth, amount = amount, dueDate = "", status = "Pending")
                            )
                            total += amount
                        }
                    }
                }
            }

            return PendingFees(
                studentId = studentId,
                totalPending = total,
                pendingMonths = pendingMonths,
                rawData = data
            )
        }

        private fun Any?.toSafeDouble(): Double {
            return when (this) {
                is Number -> this.toDouble()
                is String -> this.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        }
    }
}

data class PendingMonth(
    val month: String,
    val amount: Double,
    val dueDate: String = "",
    val status: String = "Pending"  // Pending, Paid, Overdue
) {
    companion object {
        private val MONTH_NAMES = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        /**
         * Convert numeric month keys ("1", "01", "4") to names ("January", "April").
         * If the key is already a name or unrecognised, return it as-is.
         */
        fun formatMonthKey(key: String): String {
            val num = key.trimStart('0').toIntOrNull()
            return if (num != null && num in 1..12) MONTH_NAMES[num - 1] else key
        }
    }
}

/**
 * Fee payment history record.
 * Path: Users/Parents/{parentDbKey}/{studentId}/Fees Record/
 */
data class FeePayment(
    val paymentId: String = "",
    val amount: Double = 0.0,
    val date: String = "",
    val month: String = "",
    val mode: String = "",       // Cash, Online, Cheque, etc.
    val receiptNo: String = "",
    val remarks: String = "",
    val rawData: Map<String, Any?> = emptyMap()
) {
    companion object {
        fun fromMap(paymentId: String, data: Map<String, Any?>): FeePayment {
            return FeePayment(
                paymentId = paymentId,
                amount = (data["amount"] ?: data["Amount"]).toSafeDouble(),
                date = (data["date"] ?: data["Date"] ?: "").toString(),
                month = (data["month"] ?: data["Month"] ?: "").toString(),
                mode = (data["mode"] ?: data["Mode"] ?: "").toString(),
                receiptNo = (data["receipt_no"] ?: data["Receipt_no"] ?: data["receiptNo"] ?: "").toString(),
                remarks = (data["remarks"] ?: data["Remarks"] ?: "").toString(),
                rawData = data
            )
        }

        private fun Any?.toSafeDouble(): Double {
            return when (this) {
                is Number -> this.toDouble()
                is String -> this.toDoubleOrNull() ?: 0.0
                else -> 0.0
            }
        }
    }
}

/**
 * Aggregated fee overview for the student dashboard.
 */
data class FeeOverview(
    val feeStructure: FeeStructure = FeeStructure(),
    val pendingFees: PendingFees = PendingFees(),
    val paymentHistory: List<FeePayment> = emptyList(),
    val transportFee: TransportFee? = null,
    val hostelFee: HostelFee? = null,
    val libraryFines: List<LibraryFine> = emptyList(),
    val clearanceStatus: ClearanceStatus? = null
) {
    val totalPaid: Double get() = paymentHistory.sumOf { it.amount }
}

/**
 * Transport fee for a student.
 * Path: Schools/{school}/{session}/Fees/Student_Fee_Items/{studentId}/Transport
 */
data class TransportFee(
    val routeId: String = "",
    val routeName: String = "",
    val amount: Double = 0.0,
    val period: String = "Monthly",
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "active"
) {
    companion object {
        fun fromMap(data: Map<String, Any?>): TransportFee {
            return TransportFee(
                routeId = (data["route_id"] ?: "").toString(),
                routeName = (data["route_name"] ?: "").toString(),
                amount = (data["amount"] as? Number)?.toDouble() ?: (data["amount"]?.toString()?.toDoubleOrNull() ?: 0.0),
                period = (data["period"] ?: "Monthly").toString(),
                startDate = (data["start_date"] ?: "").toString(),
                endDate = (data["end_date"] ?: "").toString(),
                status = (data["status"] ?: "active").toString()
            )
        }
    }
}

/**
 * Hostel fee for a student.
 * Path: Schools/{school}/{session}/Fees/Student_Fee_Items/{studentId}/Hostel
 */
data class HostelFee(
    val building: String = "",
    val room: String = "",
    val roomType: String = "",
    val amount: Double = 0.0,
    val messCharges: Double = 0.0,
    val period: String = "Monthly",
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "active"
) {
    companion object {
        fun fromMap(data: Map<String, Any?>): HostelFee {
            return HostelFee(
                building = (data["building"] ?: "").toString(),
                room = (data["room"] ?: "").toString(),
                roomType = (data["room_type"] ?: "").toString(),
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                messCharges = (data["mess_charges"] as? Number)?.toDouble() ?: 0.0,
                period = (data["period"] ?: "Monthly").toString(),
                startDate = (data["start_date"] ?: "").toString(),
                endDate = (data["end_date"] ?: "").toString(),
                status = (data["status"] ?: "active").toString()
            )
        }
    }
}

/**
 * Library fine for a student.
 */
data class LibraryFine(
    val bookId: String = "",
    val bookTitle: String = "",
    val fineAmount: Double = 0.0,
    val dueDate: String = ""
) {
    companion object {
        fun fromMap(bookId: String, data: Map<String, Any?>): LibraryFine {
            return LibraryFine(
                bookId = bookId,
                bookTitle = (data["title"] ?: "").toString(),
                fineAmount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                dueDate = (data["due_date"] ?: "").toString()
            )
        }
    }
}

/**
 * TC / clearance status.
 * Path: Schools/{school}/{session}/Fees/Clearance/{studentId}
 */
data class ClearanceStatus(
    val feesClear: Boolean = false,
    val feesDues: Double = 0.0,
    val libraryClear: Boolean = false,
    val libraryDues: Double = 0.0,
    val libraryUnreturnedBooks: Int = 0,
    val hostelClear: Boolean = false,
    val hostelDues: Double = 0.0,
    val transportClear: Boolean = false,
    val transportDues: Double = 0.0,
    val allClear: Boolean = false,
    val totalDues: Double = 0.0,
    val checkedAt: String = ""
) {
    companion object {
        fun fromMap(data: Map<String, Any?>): ClearanceStatus {
            val feesClear = data["fees_clear"] as? Boolean ?: false
            val libraryClear = data["library_clear"] as? Boolean ?: false
            val hostelClear = data["hostel_clear"] as? Boolean ?: false
            val transportClear = data["transport_clear"] as? Boolean ?: false
            val totalDues = (data["total_dues"] as? Number)?.toDouble() ?: 0.0

            // Compute allClear from per-module flags if the server didn't set it explicitly
            val allClear = data["all_clear"] as? Boolean
                ?: (feesClear && libraryClear && hostelClear && transportClear && totalDues <= 0.0)

            return ClearanceStatus(
                feesClear = feesClear,
                feesDues = (data["fees_dues"] as? Number)?.toDouble() ?: 0.0,
                libraryClear = libraryClear,
                libraryDues = (data["library_dues"] as? Number)?.toDouble() ?: 0.0,
                libraryUnreturnedBooks = (data["library_unreturned_books"] as? Number)?.toInt() ?: 0,
                hostelClear = hostelClear,
                hostelDues = (data["hostel_dues"] as? Number)?.toDouble() ?: 0.0,
                transportClear = transportClear,
                transportDues = (data["transport_dues"] as? Number)?.toDouble() ?: 0.0,
                allClear = allClear,
                totalDues = totalDues,
                checkedAt = (data["checked_at"] ?: "").toString()
            )
        }
    }
}

/**
 * Payment intent for mobile-initiated online payment.
 * Path: Schools/{school}/{session}/Fees/Payment_Intents/{intentId}
 */
data class PaymentIntent(
    val intentId: String = "",
    val studentId: String = "",
    val amount: Double = 0.0,
    val feeMonths: List<String> = emptyList(),
    val status: String = "requested",
    val gatewayOrderId: String = "",
    val createdAt: String = "",
    val completedAt: String = "",
    val receiptNo: String = ""
) {
    companion object {
        fun fromMap(intentId: String, data: Map<String, Any?>): PaymentIntent {
            val months = when (val m = data["fee_months"]) {
                is List<*> -> m.filterIsInstance<String>()
                is Map<*, *> -> m.values.filterIsInstance<String>()
                else -> emptyList()
            }
            return PaymentIntent(
                intentId = intentId,
                studentId = (data["student_id"] ?: "").toString(),
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                feeMonths = months,
                status = (data["status"] ?: "requested").toString(),
                gatewayOrderId = (data["gateway_order_id"] ?: "").toString(),
                createdAt = (data["created_at"] ?: "").toString(),
                completedAt = (data["completed_at"] ?: "").toString(),
                receiptNo = (data["receipt_no"] ?: "").toString()
            )
        }
    }
}
