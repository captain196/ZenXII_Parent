package com.schoolsync.parent.ui.support

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.schoolsync.parent.data.repository.firestore.SchoolFirestoreRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Who a parent should speak to about a member of staff.
 *
 * WHY THIS EXISTS AT ALL
 * ----------------------
 * "Staff Conduct" is offered as a support category, but v1 has no confidential
 * lane: `SupportFirestoreRepository` writes `lane = "normal"`, and the rules
 * hard-enforce `lane == 'normal'` on create. A conduct report therefore lands in
 * the ordinary triage queue, attributed, readable by every staff member holding
 * the Support module — potentially including the person being reported.
 *
 * Rather than advertise a discretion the system does not provide, tapping that
 * category routes here instead of composing a ticket. Nothing is written.
 *
 * RESOLUTION ORDER — deliberately falls back rather than requiring setup
 * ---------------------------------------------------------------------
 * A screen that needs a new per-school config field is broken for every school
 * until an admin fills it in, and that silence is exactly the failure class this
 * module keeps producing. So each field degrades independently:
 *
 *   name   grievance_contact.name  →  principal  →  (omitted)
 *   phone  grievance_contact.number →  school phone  →  (button hidden)
 *   email  grievance_contact.email  →  school email  →  (button hidden)
 *
 * With nothing configured at all the parent still gets the school's own phone
 * and email, which every school has. If even those are missing they are told to
 * ask at the office — never a dead end, never a blank screen.
 *
 * `grievance_contact` mirrors the shape of the existing `forget_password_details`
 * map ({name, email, number}), so an admin UI for it has a precedent to copy.
 */
@HiltViewModel
class ConductContactViewModel @Inject constructor(
    private val schoolRepository: SchoolFirestoreRepository
) : ViewModel() {

    data class Contact(
        val name: String = "",
        val phone: String = "",
        val email: String = "",
        val isLoading: Boolean = true,
        /**
         * R22 — did this contact come from a DESIGNATED grievance officer, or
         * from the school's general details because none is configured?
         *
         * The fallback chain above is deliberate and stays. What was missing is
         * that nobody was told which arm produced the answer. On this screen the
         * distinction is not cosmetic: a complaint about a staff member may be
         * routed to the office that staff member works in, and a complainant is
         * entitled to know whether they are calling a designated grievance
         * officer or the school's front desk before they speak.
         *
         * Confirmed against live data (SD-T2 R22): in this tenant
         * grievance_contact, principal_name, principal_phone and principal_email
         * are ALL absent, so the dialog served the school's general phone/email
         * and said nothing about it.
         */
        val isDesignated: Boolean = false
    ) {
        /** True when we have nothing actionable and must fall back to advice. */
        val isEmpty: Boolean get() = phone.isBlank() && email.isBlank()
    }

    companion object {
        /**
         * These two sanitisers are a security control, not tidiness.
         *
         * The values come from the school document and are interpolated into a
         * `tel:` / `mailto:` URI. Mail clients parse mailto QUERY PARAMETERS, so
         * an address of
         *
         *     office@school.com?to=attacker@example.com&subject=...
         *
         * opens a compose window that also addresses the attacker — silently
         * copying a staff-conduct report, the most sensitive message this module
         * carries. `tel:` is milder but `,` and `;` append DTMF digits after the
         * dial, so an injected suffix can redirect where the call lands.
         *
         * It needs control of the school doc to exploit (a compromised or
         * careless admin), which is why this is a hardening control rather than
         * an open hole. Reject rather than repair: a value that does not look
         * like a plain address or number is dropped, and the button simply does
         * not appear — the parent still gets the fallback advice.
         */
        private val EMAIL = Regex("^[A-Za-z0-9._%+-]{1,64}@[A-Za-z0-9.-]{1,255}\\.[A-Za-z]{2,24}$")

        fun safeEmail(raw: String): String {
            val v = raw.trim()
            return if (EMAIL.matches(v)) v else ""
        }

        /**
         * Dial-safe characters only, and REJECTED rather than repaired.
         *
         * Filtering would be worse than refusing: "+9187654321,999" would become
         * "+9187654321999" — a different number, dialled silently, with the
         * parent none the wiser. `,` and `;` are the interesting ones because a
         * dialler treats them as DTMF separators after connection.
         */
        private val PHONE_ALLOWED = Regex("^[0-9+\\-() ]+$")

        fun safePhone(raw: String): String {
            val v = raw.trim()
            if (v.isEmpty() || !PHONE_ALLOWED.matches(v)) return ""
            val digits = v.count { it.isDigit() }
            return if (digits in 6..15) v else ""
        }
    }

    private val _contact = MutableStateFlow(Contact())
    val contact: StateFlow<Contact> = _contact.asStateFlow()

    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        viewModelScope.launch {
            val config = runCatching { schoolRepository.getSchoolConfig() }.getOrNull()
            val school = runCatching { schoolRepository.getSchool().getOrNull() }.getOrNull()

            @Suppress("UNCHECKED_CAST")
            val grievance = config?.get("grievance_contact") as? Map<String, Any?>

            fun pick(vararg candidates: String?): String =
                candidates.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()

            // Designated only when the school actually configured a grievance
            // contact reachable in some way. A name alone is not enough: a name
            // with the school's general number is still the front desk.
            val designated = !(grievance?.get("number")?.toString()).isNullOrBlank()
                          || !(grievance?.get("email")?.toString()).isNullOrBlank()

            _contact.value = Contact(
                name = pick(
                    grievance?.get("name")?.toString(),
                    config?.get("principal")?.toString()
                ),
                phone = safePhone(pick(
                    grievance?.get("number")?.toString(),
                    config?.get("phone")?.toString(),
                    school?.phone
                )),
                email = safeEmail(pick(
                    grievance?.get("email")?.toString(),
                    config?.get("email")?.toString(),
                    school?.email
                )),
                isLoading = false,
                isDesignated = designated
            )
        }
    }
}
