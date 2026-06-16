// A real String.format kept on purpose, suppressed with a reason.
val s = String.format("%d", 1) // crash-check:ignore jvm-string-format — Android-guarded expect/actual
