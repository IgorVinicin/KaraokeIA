fun main() {
    // Synthetic format-only input. Never read or output a device/user credential.
    val key = "AIza" + "x".repeat(35)
    val valid = listOf(key, "  $key\n", "\"$key\"", "'$key'", "  \" $key \"  ", "AIza" + "_-".repeat(17) + "x")
    valid.forEach { check(normalizeYouTubeKey(it).matches(Regex("AIza[A-Za-z0-9_-]{35}"))) }
    val invalid = listOf("", " ", key.dropLast(1), key + "x", "ABcd" + "x".repeat(35),
        key.dropLast(1) + "+", key.dropLast(1) + "/", "\"$key", "$key'", key + "\nextra", "AIza" + "á".repeat(35))
    invalid.forEach { input ->
        val failure = runCatching { normalizeYouTubeKey(input) }.exceptionOrNull()
        check(failure is IllegalArgumentException)
        check(failure.message == "Informe uma chave válida da API do YouTube.")
    }
    println("PASS: 6 accepted normalization/format cases, 11 rejected cases, generic error messages")
}
