package edu.artic.search

import edu.artic.analytics.AnalyticsAction
import edu.artic.analytics.AnalyticsTracker
import edu.artic.analytics.EventCategoryName
import edu.artic.analytics.ScreenCategoryName
import edu.artic.db.models.ArticExhibition
import edu.artic.db.models.ArticObject
import edu.artic.db.models.ArticSearchArtworkObject
import edu.artic.db.models.ArticTour
import edu.artic.viewmodel.NavViewViewModel
import edu.artic.viewmodel.Navigate
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import javax.inject.Inject

open class SearchBaseViewModel @Inject constructor(
        protected val analyticsTracker: AnalyticsTracker,
        protected val searchResultsManager: SearchResultsManager)
    : NavViewViewModel<SearchBaseViewModel.NavigationEndpoint>() {

    sealed class NavigationEndpoint {
        data class TourDetails(val tour: ArticTour) : NavigationEndpoint()
        data class ExhibitionDetails(val exhibition: ArticExhibition) : NavigationEndpoint()
        data class ArtworkDetails(val articObject: ArticSearchArtworkObject) : NavigationEndpoint()
        data class ArtworkOnMap(val articObject: ArticObject) : NavigationEndpoint()
        data class AmenityOnMap(val type: SuggestedMapAmenities) : NavigationEndpoint()
        object Web : NavigationEndpoint()

    }

    val cells: Subject<List<SearchBaseCellViewModel>> = BehaviorSubject.create()


    fun onClickItem(pos: Int, viewModel: SearchBaseCellViewModel) {
        val searchText = (searchResultsManager.currentSearchText as BehaviorSubject<String>).value.orEmpty()
        when (viewModel) {
            is SearchTourCellViewModel -> {
                analyticsTracker.reportEvent(
                        EventCategoryName.SearchTour,
                        viewModel.articTour.title,
                        searchText
                )
                navigateTo.onNext(
                        Navigate.Forward(NavigationEndpoint.TourDetails(viewModel.articTour))
                )
            }
            is SearchExhibitionCellViewModel -> {
                analyticsTracker.reportEvent(
                        EventCategoryName.SearchExhibition,
                        viewModel.articExhibition.title,
                        searchText
                )
                navigateTo.onNext(
                        Navigate.Forward(
                                NavigationEndpoint.ExhibitionDetails(viewModel.articExhibition)
                        )
                )
            }
            is SearchArtworkCellViewModel -> {
                analyticsTracker.reportEvent(
                        EventCategoryName.SearchArtwork,
                        viewModel.articObject.title,
                        searchText
                )
                navigateTo.onNext(
                        Navigate.Forward(
                                NavigationEndpoint.ArtworkDetails(viewModel.articObject)
                        )
                )
            }
            is SearchAmenitiesCellViewModel -> {
                when (viewModel.type) {
                    SuggestedMapAmenities.Dining -> {
                        analyticsTracker.reportEvent(ScreenCategoryName.Map, AnalyticsAction.mapShowDining)
                    }
                    SuggestedMapAmenities.Restrooms -> {
                        analyticsTracker.reportEvent(ScreenCategoryName.Map, AnalyticsAction.mapShowRestrooms)
                    }
                    SuggestedMapAmenities.GiftShop -> {
                        analyticsTracker.reportEvent(ScreenCategoryName.Map, AnalyticsAction.mapShowGiftShops)
                    }
                    SuggestedMapAmenities.MembersLounge -> {
                        analyticsTracker.reportEvent(ScreenCategoryName.Map, AnalyticsAction.mapShowMemberLounge)
                    }
                }
                navigateTo.onNext(
                        Navigate.Forward(
                                NavigationEndpoint.AmenityOnMap(viewModel.type)
                        )
                )
            }
            is SearchEmptyCellViewModel -> {
                navigateTo.onNext(Navigate.Forward(NavigationEndpoint.Web))
            }
            is SearchCircularCellViewModel -> {
                viewModel.artWork?.let { articObject ->
                    navigateTo.onNext(
                            Navigate.Forward(
                                    NavigationEndpoint.ArtworkOnMap(articObject)
                            )
                    )

                }
            }
            is SearchTextCellViewModel -> {
                analyticsTracker.reportEvent(
                        ScreenCategoryName.Search,
                        AnalyticsAction.searchAutocomplete,
                        viewModel.textString
                )
                searchResultsManager.search(viewModel.textString)
            }
            is SearchHeaderCellViewModel -> {
                //TODO: handle this case
            }
        }
    }
}