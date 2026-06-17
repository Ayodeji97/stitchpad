// Clean file — must produce zero findings.
val data = snap.data<UserDto>()
single<HttpClient> { createClient() }
val s = buildString { append(1) }
val days = date.toEpochDays().toLong()
