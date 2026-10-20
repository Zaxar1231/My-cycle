package io.github.zaxar1231.mycycle.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.zaxar1231.mycycle.core.domain.time.DateProvider
import io.github.zaxar1231.mycycle.core.domain.time.SystemDateProvider

@Module
@InstallIn(SingletonComponent::class)
interface TimeModule {
    @Binds
    fun bindDateProvider(impl: SystemDateProvider): DateProvider
}
