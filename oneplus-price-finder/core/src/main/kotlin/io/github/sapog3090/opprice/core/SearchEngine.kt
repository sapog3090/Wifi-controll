package io.github.sapog3090.opprice.core

import io.github.sapog3090.opprice.core.sources.OfferSource
import io.github.sapog3090.opprice.core.text.TitleAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.math.roundToLong

sealed interface SourceState {
    data object Loading : SourceState
    data class Done(val offers: List<Offer>) : SourceState
    data class Failed(val kind: FailureKind, val message: String) : SourceState
}

data class SourceUpdate(val source: Source, val state: SourceState)

class SearchEngine(sources: List<OfferSource>, private val rates: RateProvider) {

    private val sources = sources.associateBy { it.source }

    fun browserUrl(source: Source, query: String): String? = sources[source]?.browserUrl(query)

    /** Опрашивает площадки параллельно; каждая сообщает о себе, как только ответила. */
    fun search(query: SearchQuery): Flow<SourceUpdate> = channelFlow {
        for (source in query.sources) {
            val impl = sources[source] ?: continue
            launch {
                send(SourceUpdate(source, SourceState.Loading))
                send(SourceUpdate(source, run(impl, query.text)))
            }
        }
    }

    suspend fun searchAll(query: SearchQuery): Map<Source, SourceState> =
        search(query).filter { it.state != SourceState.Loading }.toList().associate { it.source to it.state }

    private suspend fun run(impl: OfferSource, text: String): SourceState = try {
        val listings = impl.search(text)
        val offers = listings.mapNotNull { toOffer(it, impl) }
        if (listings.isNotEmpty() && offers.isEmpty()) {
            SourceState.Failed(FailureKind.OTHER, "не удалось получить курс валют")
        } else {
            SourceState.Done(offers)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: SourceException) {
        SourceState.Failed(e.kind, e.message.orEmpty())
    } catch (e: IOException) {
        SourceState.Failed(FailureKind.NETWORK, "нет соединения (${e.javaClass.simpleName})")
    } catch (e: Exception) {
        SourceState.Failed(FailureKind.OTHER, e.message ?: e.javaClass.simpleName)
    }

    private suspend fun toOffer(listing: Listing, impl: OfferSource): Offer? {
        val rate = rates.rubPer(listing.price.currency) ?: return null
        val analysis = TitleAnalyzer.analyze(
            title = listing.title,
            brand = listing.brand,
            conditionText = listing.conditionText,
            defaultCondition = impl.defaultCondition,
        )
        return Offer(
            listing = listing,
            priceRub = (listing.price.amount * rate).roundToLong(),
            kind = analysis.kind,
            model = analysis.model,
            memory = analysis.memory,
            condition = analysis.condition,
        )
    }
}
