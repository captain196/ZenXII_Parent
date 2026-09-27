package com.schoolsync.parent.data.model.firestore

import com.google.firebase.firestore.DocumentId

data class SalarySlipDoc(
    @DocumentId
    val id: String = "",
    val schoolId: String = "",
    val month: String = "",
    val staffId: String = "",
    val staffName: String = "",
    val empId: String = "",
    val department: String = "",
    val earnings: Map<String, Double> = emptyMap(),    // basic, hra, da, transport, special
    val deductions: Map<String, Double> = emptyMap(),  // pf, esi, tds, professionalTax, lwp
    val grossEarnings: Double = 0.0,
    val totalDeductions: Double = 0.0,
    val netPayable: Double = 0.0,
    // Day counts are FRACTIONAL on the server and must be Double.
    //
    // The admin panel produces half-days three independent ways: an attendance
    // 'M' mark, a half-day leave, and a school-declared Calendar Half-Day —
    // which makes workingDays 21.5, lwpDays 0.5 and presentDays 25.25.
    //
    // These were Int. Firestore's CustomClassMapper narrows a stored double
    // into an Int field via Number.intValue() SILENTLY, with no exception, so
    // 21.5 would arrive as 21 and 0.5 as 0. The Teacher app carried the same
    // defect against a live screen and it went unnoticed until a payslip was
    // read back and compared with the stored document.
    //
    // Nothing in this app references SalarySlipDoc today, so this is a latent
    // trap rather than a live bug — corrected now so wiring it up cannot
    // reintroduce a defect that has already been fixed once elsewhere. Matches
    // the canonical server contract (Hr::_buildPayslipDoc writes floats) and
    // the Teacher app's SalarySlipDoc.
    val workingDays: Double = 0.0,
    val presentDays: Double = 0.0,
    val daysAbsent: Double = 0.0,
    val lwpDays: Double = 0.0,
    val paidLeaveDays: Double = 0.0,
    val overtimeHours: Double = 0.0,
    val overtimePay: Double = 0.0,
    val arrears: Double = 0.0,
    val status: String = "generated",      // generated, approved, disbursed
    val bankRefNo: String = "",
    val disbursedAt: Any? = null,
    val generatedAt: Any? = null
)
