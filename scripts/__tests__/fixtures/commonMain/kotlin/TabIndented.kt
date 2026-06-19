// Tab-indented crash pattern — must still be detected (tab-line fix).
fun fetchData() {
	val data = snap.data<Map<String, Any?>>()
}
