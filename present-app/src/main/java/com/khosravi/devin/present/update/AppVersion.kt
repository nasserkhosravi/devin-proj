package com.khosravi.devin.present.update

/**
 * A `major.minor.patch` version. [parse] accepts plain versions (`4.5.0`, `v4.5.0`) and the
 * presenter release tag shapes used on GitHub (`presenter_4.4.0`, `Presenter/4.1.0`).
 */
data class AppVersion(val major: Int, val minor: Int, val patch: Int) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val VERSION_PATTERN =
            Regex("^(?:presenter[_/-]?)?v?(\\d+)\\.(\\d+)\\.(\\d+)$", RegexOption.IGNORE_CASE)

        fun parse(value: String): AppVersion? {
            val match = VERSION_PATTERN.matchEntire(value.trim()) ?: return null
            val (major, minor, patch) = match.destructured
            return AppVersion(
                major.toIntOrNull() ?: return null,
                minor.toIntOrNull() ?: return null,
                patch.toIntOrNull() ?: return null,
            )
        }
    }
}
