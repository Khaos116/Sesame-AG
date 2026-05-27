package io.github.aoguai.sesameag.entity

import io.github.aoguai.sesameag.util.maps.IdMapManager
import io.github.aoguai.sesameag.util.maps.SportsEnergyExchangeMap

class SportsEnergyExchange(i: String, n: String) : MapperEntity() {
    init {
        id = i
        name = n
    }

    companion object {
        fun getList(): List<SportsEnergyExchange> {
            return IdMapManager.getInstance(SportsEnergyExchangeMap::class.java).map
                .map { (key, value) -> SportsEnergyExchange(key, value) }
        }
    }
}
