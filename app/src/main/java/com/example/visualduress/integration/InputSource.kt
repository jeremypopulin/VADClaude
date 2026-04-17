package com.example.visualduress.integration

/**
 * Sealed interface representing a generic input source.
 *
 * All hardware/integration backends implement this interface.
 * The ViewModel only ever calls these methods — it has no knowledge
 * of whether it is talking to Moxa, Modbus, or Inception.
 *
 * To add a new protocol, implement this interface and register it
 * in InputSourceFactory.
 */
interface InputSource {

    /**
     * Perform a single poll/fetch cycle and return the current
     * state of all inputs as a map of [slotIndex -> value].
     *
     * For digital inputs:  0 = inactive, 1 = active
     * For Inception:       mapped from InputPublicState bit flags
     *
     * @throws Exception on connection failure, timeout, or auth failure.
     *   The ViewModel will catch this and mark the source as offline.
     */
    suspend fun poll(): Map<Int, Int>

    /**
     * Called once when the source is activated. Use to open
     * persistent connections, authenticate sessions, etc.
     * May be a no-op for stateless sources.
     */
    suspend fun connect() {}

    /**
     * Called when the source is deactivated or replaced.
     * Use to clean up sessions, sockets, and coroutines.
     */
    suspend fun disconnect() {}

    /**
     * Human-readable name used in logs and the connection status bar.
     */
    val displayName: String
}

/**
 * Enum used to persist the user's chosen input source in SharedPreferences.
 */
enum class InputSourceType(val displayName: String) {
    MOXA_REST("Moxa ioLogik REST"),
    MODBUS_TCP("Modbus TCP"),
    INCEPTION("Inner Range Inception")
}
