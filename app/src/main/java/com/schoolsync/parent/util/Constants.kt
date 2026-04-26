package com.schoolsync.parent.util

import java.util.Calendar

/**
 * Central constants for Firebase paths, attendance codes, and other app-wide values.
 * All Firebase paths match the exact structure used by the admin panel.
 */
object Constants {

    // Node.js API removed — auth now via Firebase Auth directly
    // const val BASE_URL = "https://project2-2-80nu.onrender.com/"

    /** Firebase RTDB path builders */
    object Firebase {

        // ── Index / Lookup ───────────────────────────────────────────────

        /** Indexes/School_codes/{mongoSchoolId} -> Firebase school key */
        fun schoolCodePath(mongoSchoolId: String): String =
            "Indexes/School_codes/$mongoSchoolId"

        // ── Student Profile ──────────────────────────────────────────────

        /** Users/Parents/{parentDbKey}/{studentId}/ */
        fun studentProfilePath(parentDbKey: String, studentId: String): String =
            "Users/Parents/$parentDbKey/$studentId"

        // ── Attendance ───────────────────────────────────────────────────

        /**
         * Schools/{schoolCode}/{session}/{class}/{section}/Students/{studentId}/Attendance/{Month}
         * Returns a single string like "PPAPL..." where each char = one day's status.
         */
        fun attendancePath(
            schoolCode: String,
            session: String,
            className: String,
            section: String,
            studentId: String,
            month: String
        ): String =
            "Schools/$schoolCode/$session/${classKey(className)}/${sectionKey(section)}/Students/$studentId/Attendance/$month"

        /** Base path for all attendance months */
        fun attendanceBasePath(
            schoolCode: String,
            session: String,
            className: String,
            section: String,
            studentId: String
        ): String =
            "Schools/$schoolCode/$session/${classKey(className)}/${sectionKey(section)}/Students/$studentId/Attendance"

        // ── Results ──────────────────────────────────────────────────────

        /** Schools/{schoolCode}/{session}/Results/Computed/{examId}/Class {class}/Section {section}/{studentId} */
        fun resultPath(
            schoolCode: String,
            session: String,
            examId: String,
            className: String,
            section: String,
            studentId: String
        ): String =
            "Schools/$schoolCode/$session/Results/Computed/$examId/${classKey(className)}/${sectionKey(section)}/$studentId"

        /** Schools/{schoolCode}/{session}/Results/Computed/ (list exam IDs) */
        fun computedResultsBasePath(schoolCode: String, session: String): String =
            "Schools/$schoolCode/$session/Results/Computed"

        // ── Exams Schedule ───────────────────────────────────────────────

        /** Schools/{schoolCode}/{session}/Class {class}/Section {section}/Exams/{examName} */
        fun examSchedulePath(
            schoolCode: String,
            session: String,
            className: String,
            section: String,
            examName: String
        ): String =
            "Schools/$schoolCode/$session/${classKey(className)}/${sectionKey(section)}/Exams/$examName"

        /** Schools/{schoolCode}/{session}/Class {class}/Section {section}/Exams/ */
        fun examsBasePath(
            schoolCode: String,
            session: String,
            className: String,
            section: String
        ): String =
            "Schools/$schoolCode/$session/${classKey(className)}/${sectionKey(section)}/Exams"

        // ── Fees ─────────────────────────────────────────────────────────

        /** Schools/{schoolCode}/{session}/Accounts/Fees/Classes Fees/{class}/{section}/ */
        fun feeStructurePath(
            schoolCode: String,
            session: String,
            className: String,
            section: String
        ): String =
            "Schools/$schoolCode/$session/Accounts/Fees/Classes Fees/${classKey(className)}/${sectionKey(section)}"

        /** Schools/{schoolCode}/{session}/Accounts/Pending_fees/{studentId} */
        fun pendingFeesPath(
            schoolCode: String,
            session: String,
            studentId: String
        ): String =
            "Schools/$schoolCode/$session/Accounts/Pending_fees/$studentId"

        /** Users/Parents/{parentDbKey}/{studentId}/Fees Record/ */
        fun feeHistoryPath(parentDbKey: String, studentId: String): String =
            "Users/Parents/$parentDbKey/$studentId/Fees Record"

        /** Schools/{schoolCode}/{session}/Fees/Student_Fee_Items/{studentId} */
        fun studentFeeItemsPath(schoolCode: String, session: String, studentId: String): String =
            "Schools/$schoolCode/$session/Fees/Student_Fee_Items/$studentId"

        /** Schools/{schoolCode}/{session}/Fees/Clearance/{studentId} */
        fun clearancePath(schoolCode: String, session: String, studentId: String): String =
            "Schools/$schoolCode/$session/Fees/Clearance/$studentId"

        /** Schools/{schoolCode}/{session}/Fees/Defaulters/{studentId} */
        fun defaulterPath(schoolCode: String, session: String, studentId: String): String =
            "Schools/$schoolCode/$session/Fees/Defaulters/$studentId"

        /** Schools/{schoolCode}/{session}/Fees/Payment_Intents */
        fun paymentIntentsPath(schoolCode: String, session: String): String =
            "Schools/$schoolCode/$session/Fees/Payment_Intents"

        // ── Timetable ────────────────────────────────────────────────────

        /** Schools/{schoolCode}/{session}/Class {class}/Section {section}/Time_table/ */
        fun timetablePath(
            schoolCode: String,
            session: String,
            className: String,
            section: String
        ): String =
            "Schools/$schoolCode/$session/${classKey(className)}/${sectionKey(section)}/Time_table"

        // ── Communication ────────────────────────────────────────────────

        /** Schools/{schoolCode}/Communication/Notices/ */
        fun noticesPath(schoolCode: String): String =
            "Schools/$schoolCode/Communication/Notices"

        /** Schools/{schoolCode}/Communication/Messages/Inbox/parent/{parentDbKey}/ */
        fun messagesInboxPath(schoolCode: String, parentDbKey: String): String =
            "Schools/$schoolCode/Communication/Messages/Inbox/parent/$parentDbKey"

        /** Schools/{schoolCode}/Communication/Messages/Chat/{conversationId}/ */
        fun chatPath(schoolCode: String, conversationId: String): String =
            "Schools/$schoolCode/Communication/Messages/Chat/$conversationId"

        // ── Transport ────────────────────────────────────────────────────

        /** Schools/{schoolCode}/Operations/Transport/Assignments/{studentId}/ */
        fun transportPath(schoolCode: String, studentId: String): String =
            "Schools/$schoolCode/Operations/Transport/Assignments/$studentId"

        // ── Events ───────────────────────────────────────────────────────

        /** Schools/{schoolCode}/Events/List/ */
        fun eventsPath(schoolCode: String): String =
            "Schools/$schoolCode/Events/List"

        /** Schools/{schoolCode}/Events/Media/{eventId}/ */
        fun eventMediaPath(schoolCode: String, eventId: String): String =
            "Schools/$schoolCode/Events/Media/$eventId"

        /** Schools/{schoolCode}/Events/Participants/{eventId}/ */
        fun eventParticipantsPath(schoolCode: String, eventId: String): String =
            "Schools/$schoolCode/Events/Participants/$eventId"

        // ── Gallery ──────────────────────────────────────────────────────

        /** Schools/{schoolCode}/Gallery/Albums/ */
        fun galleryAlbumsPath(schoolCode: String): String =
            "Schools/$schoolCode/Gallery/Albums"

        /** Schools/{schoolCode}/Gallery/Media/{albumId}/ */
        fun galleryMediaPath(schoolCode: String, albumId: String): String =
            "Schools/$schoolCode/Gallery/Media/$albumId"

        // ── Homework / Study Material ────────────────────────────────────

        /** Schools/{schoolCode}/{session}/Teachers/{teacherId}/studyMaterial/Class {class}/Section {section} */
        fun homeworkPath(
            schoolCode: String,
            session: String,
            teacherId: String,
            className: String,
            section: String
        ): String =
            "Schools/$schoolCode/$session/Teachers/$teacherId/studyMaterial/${classKey(className)}/${sectionKey(section)}"

        /** Schools/{schoolCode}/{session}/Teachers/ (to enumerate teachers) */
        fun teachersBasePath(schoolCode: String, session: String): String =
            "Schools/$schoolCode/$session/Teachers"

        // ── Enhanced Homework (new path) ───────────────────────────────────

        /** Schools/{schoolCode}/{session}/Homework/Class {class}/Section {section} */
        fun enhancedHomeworkPath(
            schoolCode: String,
            session: String,
            className: String,
            section: String
        ): String =
            "Schools/$schoolCode/$session/Homework/${classKey(className)}/${sectionKey(section)}"

        /** HomeworkStatus/{schoolCode}/{hwId}/{studentId} */
        fun homeworkStatusPath(
            schoolCode: String,
            hwId: String,
            studentId: String
        ): String =
            "HomeworkStatus/$schoolCode/$hwId/$studentId"

        // ── Red Flags / Student Alerts ─────────────────────────────────────

        /** StudentFlags/{schoolCode}/{studentId} */
        fun studentFlagsPath(schoolCode: String, studentId: String): String =
            "StudentFlags/$schoolCode/$studentId"

        // ── Stories ────────────────────────────────────────────────────────

        /** Schools/{schoolCode}/Social/Stories */
        fun storiesBasePath(schoolCode: String): String =
            "Schools/$schoolCode/Social/Stories"

        /** Schools/{schoolCode}/Social/StoryViews/{storyId} */
        fun storyViewsPath(schoolCode: String, storyId: String): String =
            "Schools/$schoolCode/Social/StoryViews/$storyId"

        /** Ensure className has "Class " prefix (e.g. "9th" -> "Class 9th") */
        fun classKey(className: String): String =
            if (className.startsWith("Class ", ignoreCase = true)) className else "Class $className"

        /** Ensure section has "Section " prefix (e.g. "A" -> "Section A") */
        fun sectionKey(section: String): String =
            if (section.startsWith("Section ", ignoreCase = true)) section else "Section $section"
    }

    /** Firestore collection names */
    object Firestore {
        const val SCHOOLS = "schools"
        const val STAFF = "staff"
        const val STUDENTS = "students"
        const val PARENTS = "parents"
        const val SECTIONS = "sections"
        const val SUBJECT_ASSIGNMENTS = "subjectAssignments"
        const val USERS = "users"
        const val ATTENDANCE = "attendance"
        const val ATTENDANCE_SUMMARY = "attendanceSummary"
        const val HOMEWORK = "homework"
        const val SUBMISSIONS = "submissions"
        const val LEAVE_APPLICATIONS = "leaveApplications"
        const val EXAMS = "exams"
        const val EXAM_SCHEDULE = "examSchedule"
        const val MARKS = "marks"
        const val RESULTS = "results"
        const val TIMETABLES = "timetables"
        const val FEE_STRUCTURES = "feeStructures"
        const val FEE_DEMANDS = "feeDemands"
        const val FEE_DEFAULTERS = "feeDefaulters"
        const val FEE_RECEIPTS = "feeReceipts"
        const val PAYMENT_INTENTS = "paymentIntents"
        const val FEE_ONLINE_ORDERS = "feeOnlineOrders"
        const val FEE_ONLINE_PAYMENTS = "feeOnlinePayments"
        const val FEE_CARRY_FORWARD = "feeCarryForward"
        const val STUDENT_DISCOUNTS = "studentDiscounts"
        const val SCHOLARSHIP_AWARDS = "scholarshipAwards"
        const val FEE_RECEIPT_ALLOCATIONS = "feeReceiptAllocations"
        const val FEE_REFUND_VOUCHERS = "feeRefundVouchers"
        const val CIRCULARS = "circulars"
        const val NOTICES_FS = "notices"  // Admin Notice Board writes here (type=notice)
        const val CIRCULAR_READS = "circularReads"
        const val NOTIFICATIONS = "notifications"
        const val EVENTS = "events"
        const val GALLERY_ALBUMS = "galleryAlbums"
        const val GALLERY_MEDIA = "galleryMedia"
        const val STORIES = "stories"
        const val PTM_CONFIG = "ptmConfig"
        const val PTM_BOOKINGS = "ptmBookings"
        const val MESSAGE_TEMPLATES = "messageTemplates"

        // Transport
        const val ROUTES = "routes"
        const val VEHICLES = "vehicles"
        const val STUDENT_ROUTES = "studentRoutes"
        const val TRIP_LOGS = "tripLogs"
        const val GEO_FENCES = "geoFences"
        const val SOS_ALERTS = "sosAlerts"

        // Hostel
        const val HOSTEL_ROOMS = "hostelRooms"
        const val HOSTEL_ALLOCATIONS = "hostelAllocations"
        const val MEAL_MENU = "mealMenus"
        const val HOSTEL_COMPLAINTS = "hostelComplaints"

        // Library
        const val LIBRARY_BOOKS = "libraryBooks"
        const val LIBRARY_ISSUES = "libraryIssues"
        const val LIBRARY_FINES = "libraryFines"

        // Behavior
        const val INCIDENTS = "incidents"
        const val MERIT_POINTS = "meritPoints"
        const val BEHAVIOR_SUMMARY = "behaviorSummary"

        // HR
        const val SALARY_SLIPS = "salarySlips"
        const val APPRAISALS = "appraisals"
        const val TRAINING = "trainings"
        const val TRAINING_REGISTRATIONS = "trainingRegistrations"
        const val RECRUITMENT = "recruitments"

        // Admissions
        const val ADMISSION_CONFIG = "admissionConfig"
        const val ADMISSION_APPLICATIONS = "admissionApplications"
        const val ADMISSION_MERIT_LIST = "admissionMeritLists"

        // Advanced
        const val ASSETS = "assets"
        const val INVENTORY = "inventory"
        const val PURCHASE_ORDERS = "purchaseOrders"
        const val VENDORS = "vendors"
        const val SURVEYS = "surveys"
        const val SURVEY_RESPONSES = "surveyResponses"
        const val LOST_FOUND = "lostFound"

        // Analytics
        const val DASHBOARDS = "dashboards"
        const val AUDIT_LOG = "auditLogs"
        const val RBAC_ROLES = "rbacRoles"

        // Phase B (RTDB elimination): Student Red Flags
        const val STUDENT_FLAGS = "studentFlags"
    }

    /** Attendance status character codes */
    object Attendance {
        const val PRESENT = 'P'
        const val ABSENT = 'A'
        const val LEAVE = 'L'
        const val HOLIDAY = 'H'
        const val TRIP = 'T'
        const val VACATION = 'V'
    }

    /** DataStore preferences file name */
    const val DATASTORE_NAME = "schoolsync_prefs"

    /**
     * Get the month name from a Calendar.MONTH int (0-indexed).
     */
    fun getMonthName(calendarMonth: Int): String {
        return when (calendarMonth) {
            Calendar.JANUARY -> "January"
            Calendar.FEBRUARY -> "February"
            Calendar.MARCH -> "March"
            Calendar.APRIL -> "April"
            Calendar.MAY -> "May"
            Calendar.JUNE -> "June"
            Calendar.JULY -> "July"
            Calendar.AUGUST -> "August"
            Calendar.SEPTEMBER -> "September"
            Calendar.OCTOBER -> "October"
            Calendar.NOVEMBER -> "November"
            Calendar.DECEMBER -> "December"
            else -> "January"
        }
    }

    /**
     * Get Calendar.MONTH int from month name string.
     */
    fun getMonthIndex(monthName: String): Int {
        return when (monthName.lowercase().trim()) {
            "january" -> Calendar.JANUARY
            "february" -> Calendar.FEBRUARY
            "march" -> Calendar.MARCH
            "april" -> Calendar.APRIL
            "may" -> Calendar.MAY
            "june" -> Calendar.JUNE
            "july" -> Calendar.JULY
            "august" -> Calendar.AUGUST
            "september" -> Calendar.SEPTEMBER
            "october" -> Calendar.OCTOBER
            "november" -> Calendar.NOVEMBER
            "december" -> Calendar.DECEMBER
            else -> Calendar.JANUARY
        }
    }
}
