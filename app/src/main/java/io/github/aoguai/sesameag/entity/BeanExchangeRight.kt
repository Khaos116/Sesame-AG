package io.github.aoguai.sesameag.entity

import io.github.aoguai.sesameag.util.maps.BeanExchangeRightMap
import io.github.aoguai.sesameag.util.maps.IdMapManager

class BeanExchangeRight(i: String, n: String) : MapperEntity() {
    init {
        id = i
        name = n
    }

    companion object {
        fun getList(): List<BeanExchangeRight> {
            return IdMapManager.getInstance(BeanExchangeRightMap::class.java).map
                .map { (key, value) -> BeanExchangeRight(key, value) }
        }
    }
}
