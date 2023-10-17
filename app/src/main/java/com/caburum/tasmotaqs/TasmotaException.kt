package com.caburum.tasmotaqs

class TasmotaException(e: Exception) :
	Exception("Failed to communicate with Tasmota: " + e.message, e, true, true)
