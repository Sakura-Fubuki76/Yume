package com.sakurafubuki.yume.core.data.webdav
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import javax.inject.Inject

interface SardineFactory {
    fun create(): Sardine
}

class SardineFactoryImpl @Inject constructor() : SardineFactory {
    override fun create(): Sardine = OkHttpSardine()
}
