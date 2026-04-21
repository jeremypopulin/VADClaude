package com.example.visualduress.integration

import com.example.visualduress.model.InceptionConfig
import com.example.visualduress.model.WmsProConfig

object InputSourceFactory {

    fun create(
        type: InputSourceType,
        modbusIp: String,
        moxa2Ip: String,
        inceptionConfig: InceptionConfig,
        wmsProConfig: WmsProConfig
    ): InputSource {
        return when (type) {
            InputSourceType.MOXA_REST   -> MoxaRestInputSource(ip = modbusIp, slotOffset = 0)
            InputSourceType.MOXA_REST_2 -> MoxaRestInputSource(ip = moxa2Ip, slotOffset = 16)
            InputSourceType.MODBUS_TCP  -> ModbusTcpInputSource(ip = modbusIp)
            InputSourceType.INCEPTION   -> InceptionInputSource(config = inceptionConfig)
            InputSourceType.WMS_PRO     -> WmsProInputSource(config = wmsProConfig)
        }
    }
}