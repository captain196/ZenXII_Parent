package com.schoolsync.parent.data.maps

import com.google.firebase.firestore.PropertyName

/**
 * `mapsConfig/{schoolId}_{schoolId}` — the centralized Maps & Location
 * configuration. See [docId] for why the school id appears twice.
 *
 * Mirrors the admin panel's `Maps_config_service` contract field-for-field.
 * This document is the SINGLE source of truth for which renderer a surface
 * uses; `BuildConfig` is only ever an offline fallback.
 *
 * Only the PUBLIC half of the configuration lives here. Server-side keys are
 * kept in a separate `mapsSecrets` document whose Firestore rule denies all
 * client access, because rules gate a document and cannot hide a field.
 */
data class MapProviderConfig(
    val schoolId: String = "",

    /** `global` — one provider everywhere · `platform` — per-surface. */
    val mode: String = MODE_GLOBAL,

    @get:PropertyName("global_provider")
    @set:PropertyName("global_provider")
    var globalProvider: String = "",

    /** platform key → provider id. A blank entry falls back to the global provider. */
    @get:PropertyName("platform_provider")
    @set:PropertyName("platform_provider")
    var platformProvider: Map<String, String> = emptyMap(),

    /** provider id → { public: {...}, enabled, validation_status, … }. */
    var providers: Map<String, Map<String, Any?>> = emptyMap(),

    @get:PropertyName("default_center")
    @set:PropertyName("default_center")
    var defaultCenter: Map<String, Double?> = emptyMap(),

    @get:PropertyName("default_zoom")
    @set:PropertyName("default_zoom")
    var defaultZoom: Double = 15.0,
) {
    // ─────────────────────────────────────────────────────────────────────
    //  NO RESOLUTION LOGIC LIVES HERE ANY MORE.
    //
    //  `resolveProvider()`, `isEnabled()`, `isSupportedOn()` and
    //  `publicCredentials()` were removed once MapProviderManager began
    //  consuming the server-resolved configuration. They were a hand-ported
    //  copy of the panel's rules, and they drifted: the server grew a platform
    //  default, a capability layer and surface-awareness, and this copy never
    //  learned any of it, so an inheriting school resolved to nothing.
    //
    //  Every caller was proved absent before deletion — the four functions were
    //  referenced only from MapProviderManager.resolve()/credentialsFor(), and
    //  `isSupportedOn()` already had none at all.
    //
    //  The single resolver is `Maps_config_service::resolveCapability()`,
    //  delivered by `getClientConfig('parent')`. Do not reintroduce a second
    //  one here, however small it looks.
    // ─────────────────────────────────────────────────────────────────────

    companion object {
        const val COLLECTION = "mapsConfig"
        const val MODE_GLOBAL = "global"
        const val MODE_PLATFORM = "platform"

        /** This app's platform key. Must match `Maps_config_service::PLATFORMS`. */
        const val PLATFORM_PARENT = "parent"

        /**
         * P0 (2026-08-16) — the document id the admin panel actually writes.
         *
         * Every write goes through `Maps_config_service` →
         * `Firestore_service::setEntity('mapsConfig', $schoolId, …)`, and
         * `setEntity` prefixes the school id itself:
         *
         *     docId(entityId) = "{schoolId}_{entityId}"
         *
         * The entity id for this document IS the school id, so the canonical
         * path is `mapsConfig/{schoolId}_{schoolId}` — the same
         * `{schoolId}_{entityId}` idiom every other ZenX collection uses
         * (see FeeDemandDoc, ExamDoc, ConversationDoc).
         *
         * This used to return the bare school id. That document has never
         * existed: a live read of the project found only
         * `SCH_…_SCH_…` ids in `mapsConfig`. The listener therefore always
         * resolved to null and the app silently fell through to
         * `BuildConfig.MAP_STYLE_URL`, so no administrator's provider choice
         * has ever reached a parent's phone.
         */
        fun docId(schoolId: String): String = "${schoolId}_$schoolId"
    }
}
