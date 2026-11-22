package com.cl0udshifter.pdfeasy.core.data.update

internal sealed interface PreReleaseIdentifier : Comparable<PreReleaseIdentifier> {
    data class Numeric(
        val value: Long,
    ) : PreReleaseIdentifier {
        override fun compareTo(other: PreReleaseIdentifier): Int =
            when (other) {
                is Numeric -> value.compareTo(other.value)
                is Alphanumeric -> -1
            }
    }

    data class Alphanumeric(
        val value: String,
    ) : PreReleaseIdentifier {
        override fun compareTo(other: PreReleaseIdentifier): Int =
            when (other) {
                is Numeric -> 1
                is Alphanumeric -> value.compareTo(other.value)
            }
    }
}

/**
 * Representacao SemVer 2.0.0 para comparacao deterministica entre tags de release do GitHub e versionName.
 */
internal data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<PreReleaseIdentifier> = emptyList(),
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        val coreComparison =
            compareValuesBy(
                this,
                other,
                SemanticVersion::major,
                SemanticVersion::minor,
                SemanticVersion::patch,
            )
        return if (coreComparison != 0) {
            coreComparison
        } else {
            comparePreRelease(preRelease, other.preRelease)
        }
    }

    private fun comparePreRelease(
        left: List<PreReleaseIdentifier>,
        right: List<PreReleaseIdentifier>,
    ): Int =
        when {
            left.isEmpty() && right.isEmpty() -> 0
            left.isEmpty() -> 1
            right.isEmpty() -> -1
            else -> comparePreReleaseIdentifiers(left, right)
        }

    private fun comparePreReleaseIdentifiers(
        left: List<PreReleaseIdentifier>,
        right: List<PreReleaseIdentifier>,
    ): Int {
        val limit = minOf(left.size, right.size)
        for (index in 0 until limit) {
            val diff = left[index].compareTo(right[index])
            if (diff != 0) return diff
        }
        return left.size.compareTo(right.size)
    }

    companion object {
        private val CORE_REGEX = Regex("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?$")
        private const val GROUP_MAJOR = 1
        private const val GROUP_MINOR = 2
        private const val GROUP_PATCH = 3

        fun parse(raw: String): SemanticVersion? {
            val cleaned = raw.trim().removePrefix("v").removePrefix("V").substringBefore('+')
            val hyphenIndex = cleaned.indexOf('-')
            val corePart = if (hyphenIndex >= 0) cleaned.substring(0, hyphenIndex) else cleaned
            val preReleasePart = if (hyphenIndex >= 0) cleaned.substring(hyphenIndex + 1) else ""

            val core = parseCoreTuple(corePart) ?: return null
            return SemanticVersion(
                major = core.first,
                minor = core.second,
                patch = core.third,
                preRelease = parsePreRelease(preReleasePart),
            )
        }

        private fun parseCoreTuple(corePart: String): Triple<Int, Int, Int>? {
            val match = CORE_REGEX.matchEntire(corePart)
            val major = match?.groupValues?.get(GROUP_MAJOR)?.toIntOrNull()
            val minor = match?.groupValues?.get(GROUP_MINOR)?.toIntOrNull()
            val patch = match?.groupValues?.get(GROUP_PATCH)?.takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            return if (major != null && minor != null) Triple(major, minor, patch) else null
        }

        private fun parsePreRelease(preReleasePart: String): List<PreReleaseIdentifier> =
            if (preReleasePart.isBlank()) {
                emptyList()
            } else {
                preReleasePart
                    .split('.')
                    .filter { it.isNotEmpty() }
                    .map { token ->
                        token.toLongOrNull()?.let(PreReleaseIdentifier::Numeric)
                            ?: PreReleaseIdentifier.Alphanumeric(token)
                    }
            }
    }
}
