package artic.edu.search

import com.fuzz.rx.bindTo
import edu.artic.db.daos.ArticObjectDao
import edu.artic.db.daos.ArticTourDao
import edu.artic.db.models.ApiSearchContent
import edu.artic.db.models.ArticExhibition
import edu.artic.db.models.ArticObject
import edu.artic.db.models.ArticTour
import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject

/**
 * Handles loading and storage of search results. As well as an abstract way of keeping state of
 * current view visible
 * @author Piotr Leja (FUZZ)
 */
class SearchResultsManager(private val searchService: SearchServiceProvider,
                           private val tourDao: ArticTourDao,
                           private val articObjectDao: ArticObjectDao
) {


    private val rawSearchResults: Subject<ArticSearchResult> = BehaviorSubject.create()
    val currentSearchResults: Subject<ArticSearchResult> = BehaviorSubject.create()
    private val currentSearchText: Subject<String> = BehaviorSubject.create()
    private val showSuggestions: Subject<Boolean> = BehaviorSubject.create()

    init {
        setupTextAvailableSearchFlow()
        setupEmptyTextSearchFlow()
        Observables.combineLatest(showSuggestions, rawSearchResults)
        { showSuggestions, results ->
            if (!showSuggestions) {
                results.suggestions = emptyList()
            }
            results
        }.bindTo(currentSearchResults)
    }

    private fun setupTextAvailableSearchFlow() {
        currentSearchText
                .filter { it.isNotEmpty() }
                .flatMap { searchTerm ->
                    Observables.combineLatest(
                            showSuggestions,
                            getSuggestionsList(searchTerm),
                            loadOtherLists(searchTerm)
                    )
                }
                .map { (shouldShowSuggestionText, suggestions, searchResult) ->
                    if (shouldShowSuggestionText) {
                        searchResult.suggestions = suggestions
                    }
                    return@map searchResult

                }.bindTo(rawSearchResults)
    }

    private fun setupEmptyTextSearchFlow() {
        currentSearchText
                .filter { it.isEmpty() }
                .map {
                    ArticSearchResult(
                            "",
                            emptyList(),
                            emptyList(),
                            emptyList(),
                            emptyList()
                    )
                }
                .bindTo(currentSearchResults)
    }

    private fun getSuggestionsList(searchTerm: String): Observable<List<String>> {
        return searchService.getSuggestions(searchTerm)
                .map {
                    if (it.response().body() == null) {
                        emptyList()
                    } else {
                        it.response().body()
                    }
                }
    }

    private fun loadOtherLists(searchTerm: String): Observable<ArticSearchResult> {
        return searchService
                .loadAllMatchingContent(searchTerm)
                .flatMap { result ->
                    Observables.combineLatest(
                            if (result.artworks == null)
                                Observable.just(listOf())
                            else
                                Observable.just(
                                        result.artworks!!
                                                .filter { it.isOnView != null }
                                                .filter { it.isOnView!! }),
                            loadTours(result.tours),
                            if (result.exhibitions == null)
                                Observable.just(listOf())
                            else
                                Observable.just(result.exhibitions!!)
                    ) { artwork: List<ArticObject>, tours: List<ArticTour>, exhibitions: List<ArticExhibition> ->
                        ArticSearchResult(searchTerm, emptyList(), artwork, tours, exhibitions)
                    }
                }
    }

    private fun loadTours(tours: List<ApiSearchContent.SearchedTour>?): Observable<List<ArticTour>> {
        return if (tours == null) {
            Observable.just(emptyList())
        } else {
            tourDao.getToursByIdList(tours.map { it.tourId.toString() }).toObservable()
        }
    }

    private fun loadArtwork(artwork: List<ApiSearchContent.SearchedArtwork>?): Observable<List<ArticObject>> {
        return if (artwork == null) {
            Observable.just(emptyList())
        } else {
            articObjectDao.getObjectsByIdList(artwork.map { it.artworkId.toString() }).toObservable()
        }
    }

    fun onChangeSearchText(newText: String) {
        showSuggestions.onNext(true)
        currentSearchText.onNext(newText)
    }

    fun search() {
        showSuggestions.onNext(false)
    }


}