package com.example.visualduress.integration

import com.example.visualduress.model.InceptionConfig

/**
 * Factory that creates the appropriate InputSource implementation
 * based on the user's configured InputSourceType.
 *
 * Add new input source types here — the ViewModel and UI never need
 * to know the concrete class.
 */
object InputSourceFactory {

    fun create(
        type: InputSourceType,
        modbusIp: String,
        moxa2Ip: String,
        inceptionConfig: InceptionConfig
    ): InputSource {
        return when (type) {
            InputSourceType.MOXA_REST   -> MoxaRestInputSource(ip = modbusIp, slotOffset = 0)
            InputSourceType.MOXA_REST_2 -> MoxaRestInputSource(ip = moxa2Ip, slotOffset = 16)
            InputSourceType.MODBUS_TCP  -> ModbusTcpInputSource(ip = modbusIp)
            InputSourceType.INCEPTION   -> InceptionInputSource(config = inceptionConfig)
        }
    }
}