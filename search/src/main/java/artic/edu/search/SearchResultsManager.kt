package artic.edu.search

import com.fuzz.rx.bindTo
import edu.artic.db.daos.ArticTourDao
import edu.artic.db.models.ApiSearchContent
import edu.artic.db.models.ArticExhibition
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
class SearchResultsManager(private val searchService: SearchServiceProvider, private val tourDao: ArticTourDao) {

    val currentSearchResults: Subject<ArticSearchResult> = BehaviorSubject.create()
    val currentSearchText: Subject<String> = BehaviorSubject.create()

    init {
        setupTextAvailableSearchFlow()
        setupEmptyTextSearchFlow()
    }

    private fun setupTextAvailableSearchFlow() {
        currentSearchText
                .filter { it.isNotEmpty() }
                .flatMap { searchTerm ->
                    Observables.combineLatest(
                            getSuggestionsList(searchTerm),
                            loadOtherLists(searchTerm)
                    )
                }
                .map { (suggestions, searchResult) ->
                    searchResult.suggestions = suggestions
                    return@map searchResult

                }.bindTo(currentSearchResults)
    }

    private fun setupEmptyTextSearchFlow() {
        currentSearchText
                .filter { it.isEmpty() }
                .map {
                    ArticSearchResult(
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
                .flatMap {
                    Observables.combineLatest(
                            loadTours(it.tours),
                            if (it.exhibitions == null) Observable.just(listOf<ArticExhibition>()) else Observable.just(it.exhibitions)
                    ) { tours: List<ArticTour>, exhibitions: List<ArticExhibition> ->
                        ArticSearchResult(emptyList(), emptyList(), tours, exhibitions)
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

    fun onChangeSearchText(newText: String) {
        currentSearchText.onNext(newText)
    }

}