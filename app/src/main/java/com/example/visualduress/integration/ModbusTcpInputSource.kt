package com.example.visualduress.integration

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Input source that reads digital inputs from a Modbus TCP device.
 *
 * Uses Modbus function code 0x02 (Read Discrete Inputs).
 *
 * Default assumption: 16 discrete inputs starting at address 0x0000.
 * This covers most field devices including Moxa ioLogik in Modbus mode,
 * Wago, Phoenix Contact, and similar PLCs.
 *
 * Configure the IP in Settings > Connectivity. The port defaults to 502.
 *
 * ## Modbus TCP frame format
 *
 * Request:
 *   [TransactionID 2B] [ProtocolID 2B=0] [Length 2B] [UnitID 1B] [FuncCode 1B]
 *   [StartAddr 2B] [Quantity 2B]
 *
 * Response:
 *   [TransactionID 2B] [ProtocolID 2B] [Length 2B] [UnitID 1B] [FuncCode 1B]
 *   [ByteCount 1B] [CoilData NB...]
 */
class ModbusTcpInputSource(
    private var ip: String,
    private val port: Int = 502,
    private val unitId: Int = 1,
    private val startAddress: Int = 0x0000,
    private val quantity: Int = 16
) : InputSource {

    override val displayName = "Modbus TCP"

    fun updateIp(newIp: String) {
        ip = newIp
    }

    override suspend fun poll(): Map<Int, Int> = withContext(Dispatchers.IO) {
        val trimmedIp = ip.trim()
        Log.d("ModbusTcp", "Polling $trimmedIp:$port")

        Socket().use { socket ->
            socket.connect(InetSocketAddress(trimmedIp, port), 3000)
            socket.soTimeout = 3000

            val os: OutputStream = socket.getOutputStream()
            val inputStream: InputStream = socket.getInputStream()

            // Build Modbus TCP Read Discrete Inputs request
            val request = buildReadDiscreteInputsRequest(
                transactionId = 1,
                unitId = unitId,
                startAddress = startAddress,
                quantity = quantity
            )
            os.write(request)
            os.flush()

            // Read response header (6 bytes MBAP + 2 bytes func/bytecount)
            val header = ByteArray(9)
            var bytesRead = 0
            while (bytesRead < 9) {
                val n = inputStream.read(header, bytesRead, 9 - bytesRead)
                if (n < 0) break
                bytesRead += n
            }

            val funcCode = header[7].toInt() and 0xFF
            if (funcCode and 0x80 != 0) {
                // Exception response
                throw Exception("Modbus exception code: ${header[8].toInt() and 0xFF}")
            }

            val byteCount = header[8].toInt() and 0xFF
            val data = ByteArray(byteCount)
            var dataRead = 0
            while (dataRead < byteCount) {
                val n = inputStream.read(data, dataRead, byteCount - dataRead)
                if (n < 0) break
                dataRead += n
            }

            // Parse coil bits into slot map (1-based slot IDs)
            val result = mutableMapOf<Int, Int>()
            for (i in 0 until quantity) {
                val byteIndex = i / 8
                val bitIndex = i % 8
                if (byteIndex < data.size) {
                    val bitValue = (data[byteIndex].toInt() shr bitIndex) and 0x01
                    result[i + 1] = bitValue
                }
            }

            Log.d("ModbusTcp", "Received ${result.size} inputs from $trimmedIp")
            result
        }
    }

    private fun buildReadDiscreteInputsRequest(
        transactionId: Int,
        unitId: Int,
        startAddress: Int,
        quantity: Int
    ): ByteArray {
        val pduLength = 6  // UnitID(1) + FuncCode(1) + StartAddr(2) + Quantity(2)
        return byteArrayOf(
            // MBAP header
            (transactionId shr 8).toByte(),   // Transaction ID high
            (transactionId and 0xFF).toByte(), // Transaction ID low
            0x00, 0x00,                        // Protocol ID (always 0)
            0x00, pduLength.toByte(),          // Length
            unitId.toByte(),                   // Unit ID
            // PDU
            0x02,                              // Function code: Read Discrete Inputs
            (startAddress shr 8).toByte(),     // Start address high
            (startAddress and 0xFF).toByte(),  // Start address low
            (quantity shr 8).toByte(),         // Quantity high
            (quantity and 0xFF).toByte()       // Quantity low
        )
    }
}
