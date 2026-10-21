package io.github.zaxar1231.mycycle.core.sync

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.zaxar1231.mycycle.core.domain.account.AccountRepository
import io.github.zaxar1231.mycycle.core.domain.partner.PartnerRepository
import io.github.zaxar1231.mycycle.core.sync.partner.SupabasePartnerRepository

@Module
@InstallIn(SingletonComponent::class)
interface SyncModule {
    @Binds fun accounts(impl: SupabaseAccountRepository): AccountRepository

    @Binds fun partners(impl: SupabasePartnerRepository): PartnerRepository
}
