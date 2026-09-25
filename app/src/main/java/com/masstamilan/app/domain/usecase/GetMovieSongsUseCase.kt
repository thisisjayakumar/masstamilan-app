package com.masstamilan.app.domain.usecase

import com.masstamilan.app.data.model.SongResult
import com.masstamilan.app.data.repository.MasstamilanRepository
import javax.inject.Inject

class GetMovieSongsUseCase @Inject constructor(
    private val repository: MasstamilanRepository
) {
    suspend operator fun invoke(movieSlug: String): List<SongResult> {
        return repository.getMovieSongs(movieSlug)
    }
}
